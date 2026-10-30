package com.lobbykin;

import java.io.IOException;
import com.google.gson.JsonObject;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import java.io.Reader;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** No credentials are persisted or logged. Redirects are deliberately disabled. */
final class SessionClient
{
    private final OkHttpClient client;
    private final String url;

    SessionClient(OkHttpClient client)
    {
        this(client, "https://lobbykin.com/api/runelite/sessions");
    }

    // Endpoint override is package-private for local tests, never a user setting.
    SessionClient(OkHttpClient client, String url)
    {
        this.client = client.newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS).build();
        this.url = url;
    }

    interface Listener
    {
        void success(SessionSnapshot snapshot);
        void failure(boolean unauthorized, String message);
        default void rejected(String message) { failure(false, message); }
    }

    static boolean validToken(String token)
    {
        return token != null && token.matches("lk_rl_[0-9a-f]{64}");
    }

    Call read(String token, Listener listener)
    {
        return send(token, "sessions", null, listener);
    }

    Call setReady(String token, String group, boolean ready, Listener listener)
    {
        if (group == null || !UUID.fromString(group).toString().equals(group))
            throw new IllegalArgumentException("Invalid session.");
        JsonObject body = new JsonObject();
        body.addProperty("group_id", group);
        body.addProperty("ready", ready);
        return send(token, "ready", body.toString(), listener);
    }

    Call offerReply(String token,String group,boolean confirm,Listener listener)
    {
        UUID.fromString(group);JsonObject body=new JsonObject();body.addProperty("group_id",group);body.addProperty("confirm",confirm);
        return send(token,"offer",body.toString(),listener);
    }

    Call report(String token,JsonObject data,Listener listener)
    {
        return send(token,"event",data.toString(),listener);
    }

    private Call send(String token, String action, String body, Listener listener)
    {
        if (!validToken(token)) throw new IllegalArgumentException("Paste a valid Lobbykin access code.");
        boolean writing = body != null;
        String endpoint = writing ? url.substring(0, url.lastIndexOf('/') + 1) + action : url;
        Request.Builder builder = new Request.Builder().url(endpoint).header("Authorization", "Bearer " + token)
            .header("Accept", "application/json").header("User-Agent", "Lobbykin-RuneLite/0.9.4-rc1")
            .header("Cache-Control", "no-store");
        Request request = writing ? builder.post(RequestBody.create(MediaType.parse("application/json"), body)).build() : builder.get().build();
        Call call = client.newCall(request);
        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call ignored, IOException error)
            {
                listener.failure(false, writing ? "Could not confirm the action. Refresh before retrying." : "Could not refresh. Check your connection and retry.");
            }

            @Override
            public void onResponse(Call ignored, Response response)
            {
                try (Response result = response)
                {
                    if (result.code() == 401 || (!writing && result.code() == 403))
                    {
                        listener.failure(true, "Access expired or revoked. Get a new code on Lobbykin.");
                        return;
                    }
                    if (writing && (result.code() == 403 || result.code() == 409))
                    {
                        listener.rejected(result.code() == 403 ? "Action denied. Check your permissions and accepted character." : "Action unavailable. Refresh sessions before retrying.");
                        return;
                    }
                    if (!result.isSuccessful() || result.body() == null)
                    {
                        listener.failure(false, writing ? "Could not confirm the action. Refresh before retrying." : "Lobbykin is temporarily unavailable. Retry shortly.");
                        return;
                    }
                    if (result.body().contentLength() > 131072)
                    {
                        throw new IOException("Response too large");
                    }
                    StringBuilder text = new StringBuilder();
                    try (Reader reader = result.body().charStream())
                    {
                        char[] buffer = new char[4096];
                        int count;
                        while ((count = reader.read(buffer)) != -1)
                        {
                            if (text.length() + count > 131072)
                            {
                                throw new IOException("Response too large");
                            }
                            text.append(buffer, 0, count);
                        }
                    }
                    listener.success(SessionSnapshot.parse(text.toString()));
                }
                catch (Exception error)
                {
                    listener.failure(false, writing ? "Could not confirm the action. Refresh before retrying." : "Could not read sessions. Please retry.");
                }
            }
        });
        return call;
    }
}
