package com.lobbykin;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class LobbykinPanelTest
{
    @Test
    public void logoutClearsPreviousCharacterAndWorld() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            LobbykinPanel panel = new LobbykinPanel(() -> {});
            panel.showPlayer("GIM Jusky", 349);
            assertTrue(texts(panel).contains("GIM Jusky"));
            assertTrue(texts(panel).contains("World: 349"));
            panel.showPlayer(null, 0);
            assertFalse(texts(panel).contains("GIM Jusky"));
            assertTrue(texts(panel).contains("Not logged in"));
            assertTrue(texts(panel).contains("World: —"));
        });
    }

    @Test
    public void websiteOnlyOpensOnClick() throws Exception
    {
        AtomicBoolean opened = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            LobbykinPanel panel = new LobbykinPanel(() -> opened.set(true));
            assertFalse(opened.get());
            JButton button = (JButton) components(panel).stream()
                .filter(c -> c instanceof JButton).findFirst().get();
            button.doClick();
            assertTrue(opened.get());
        });
    }

    @Test
    public void lifecycleUiHelperSupportsBothCallerThreads() throws Exception
    {
        AtomicBoolean ran = new AtomicBoolean();
        LobbykinPlugin.onEdt(() -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            ran.set(true);
        });
        assertTrue(ran.get());
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                LobbykinPlugin.onEdt(() -> assertTrue(SwingUtilities.isEventDispatchThread()));
            }
            catch (Exception ex)
            {
                throw new AssertionError(ex);
            }
        });
    }

    @Test
    public void readyControlNeedsPermissionAndWaitsForConfirmation() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            java.util.concurrent.atomic.AtomicReference<Boolean> requested = new java.util.concurrent.atomic.AtomicReference<>();
            LobbykinPanel panel = new LobbykinPanel(() -> {}, () -> {}, token -> {}, () -> {}, () -> {}, id -> {}, (id, desired) -> requested.set(desired));
            String base = "{\"account\":\"Player\",\"scope\":\"sessions:read\",\"sessions\":[{\"id\":\"11111111-1111-4111-8111-111111111111\",\"title\":\"My session\",\"my_ready\":false,\"can_set_ready\":false}]}";
            panel.showSessions(SessionSnapshot.parse(base), java.time.Instant.now());
            assertFalse(components(panel).stream().anyMatch(c -> c instanceof JButton && ((JButton)c).getText().equals("Mark ready")));
            panel.showSessions(SessionSnapshot.parse(base.replace("can_set_ready\":false", "can_set_ready\":true")), java.time.Instant.now());
            JButton button = (JButton)components(panel).stream().filter(c -> c instanceof JButton && ((JButton)c).getText().equals("Mark ready")).findFirst().get();
            button.doClick();
            assertEquals(Boolean.TRUE, requested.get());
            assertFalse(button.isEnabled());
            assertEquals("Mark ready", button.getText());
            panel.showError("Could not confirm. Refresh first.");
            assertFalse(button.isEnabled());
            panel.showDisconnected("Revoked");
            assertFalse(components(panel).stream().anyMatch(c -> c == button));
        });
    }

    @Test
    public void offerAndTrackingControlsRespectCapabilitiesAndPendingState() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            java.util.concurrent.atomic.AtomicReference<Boolean> accepted=new java.util.concurrent.atomic.AtomicReference<>();
            LobbykinPanel panel=new LobbykinPanel(()->{});panel.configureV6((id,value)->accepted.set(value),session->{});
            String json="{\"account\":\"Player\",\"scope\":\"sessions:read\",\"sessions\":[{\"id\":\"11111111-1111-4111-8111-111111111111\",\"title\":\"Offer\",\"my_status\":\"host\",\"can_reply_offer\":true,\"offer_until\":\""+java.time.Instant.now().plusSeconds(120)+"\",\"can_track\":true}]}";
            panel.showSessions(SessionSnapshot.parse(json),java.time.Instant.now());
            JButton offer=(JButton)components(panel).stream().filter(c->c instanceof JButton&&((JButton)c).getText().startsWith("Accept place")).findFirst().get();
            assertTrue(components(panel).stream().anyMatch(c->c instanceof JButton&&((JButton)c).getText().equals("Track this session")));
            offer.doClick();assertEquals(Boolean.TRUE,accepted.get());assertFalse(offer.isEnabled());panel.showError("Unavailable");assertFalse(offer.isEnabled());panel.showDisconnected("Disconnected");panel.dispose();
        });
    }

    private static List<String> texts(Container parent)
    {
        List<String> result = new ArrayList<>();
        for (Component component : components(parent))
        {
            if (component instanceof JLabel)
            {
                result.add(((JLabel) component).getText());
            }
        }
        return result;
    }

    private static List<Component> components(Container parent)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            result.add(child);
            if (child instanceof Container)
            {
                result.addAll(components((Container) child));
            }
        }
        return result;
    }
}
