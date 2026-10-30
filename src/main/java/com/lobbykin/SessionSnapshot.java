package com.lobbykin;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;

/** Only the whitelisted, read-only session response, never a website session. */
final class SessionSnapshot
{
    String account;
    String scope;
    String expires_at;
    List<Session> sessions;
    Capabilities capabilities;
    List<Update> notifications = java.util.Collections.emptyList();

    static final class Capabilities { boolean ready_write, notifications_read, offers_write, tracking_write; }
    static final class Update { String id,group_id,kind,title,body,created_at,read_at; }
    static final class Member {String name,role;boolean ready;}
    static final class Participant {String name,state,end_reason,last_death_at;int deaths;}
    static final class Tracking { List<Participant> participants=java.util.Collections.emptyList(); int completed_runs; Integer deaths,failed_runs,interrupted_runs; List<Run> runs=java.util.Collections.emptyList(); String disclaimer; }
    static final class Run { String id,raid,status,started_at,completed_at,duration_source; int sequence,world,report_count; Integer duration_seconds; List<String> reporters=java.util.Collections.emptyList(); }

    static SessionSnapshot parse(String json)
    {
        SessionSnapshot result = new Gson().fromJson(json, SessionSnapshot.class);
        if (result == null || !"sessions:read".equals(result.scope)
            || result.account == null || result.account.length() > 120
            || result.sessions == null || result.sessions.size() > 30)
        {
            throw new IllegalArgumentException("Unexpected session response");
        }
        if(result.notifications==null)result.notifications=java.util.Collections.emptyList();
        if(result.notifications.size()>20)throw new IllegalArgumentException("Unexpected notifications");
        for(Update update:result.notifications)
        {
            if(update==null||update.id==null||update.title==null||update.title.length()>100||update.body==null||update.body.length()>300)throw new IllegalArgumentException("Unexpected notification");
            UUID.fromString(update.id);if(update.group_id!=null)UUID.fromString(update.group_id);
        }
        for (Session session : result.sessions)
        {
            if (session == null || session.id == null || !UUID.fromString(session.id).toString().equals(session.id)
                || session.title == null || session.title.length() > 200)
            {
                throw new IllegalArgumentException("Unexpected session response");
            }
            if(session.members==null)session.members=java.util.Collections.emptyList();
            if(session.members.size()>100)throw new IllegalArgumentException("Unexpected roster");
            for(Member m:session.members)if(m==null||m.name==null||m.name.length()>20)throw new IllegalArgumentException("Unexpected party member");
            if(session.tracking!=null){
                if((session.tracking.deaths!=null&&session.tracking.deaths<0)||(session.tracking.failed_runs!=null&&session.tracking.failed_runs<0)||(session.tracking.interrupted_runs!=null&&session.tracking.interrupted_runs<0))throw new IllegalArgumentException("Unexpected session totals");
                if(session.tracking.participants==null)session.tracking.participants=java.util.Collections.emptyList();
                if(session.tracking.participants.size()>100)throw new IllegalArgumentException("Unexpected participants");
                if(session.tracking.runs==null||session.tracking.runs.size()>5||session.tracking.completed_runs<0||session.tracking.completed_runs>200)throw new IllegalArgumentException("Unexpected tracking summary");
                for(Run run:session.tracking.runs)if(run==null||run.status==null||run.reporters==null||run.reporters.size()>10)throw new IllegalArgumentException("Unexpected run summary");
            }
        }
        return result;
    }

    static final class Session
    {
        String id;
        String title;
        String activity;
        String mode;
        String session_type;
        String start_at;
        String expires_at;
        Integer duration_minutes;
        Integer world;
        String meeting_point;
        String voice;
        String raid_phase,raid_lease_until;
        List<Member> members=java.util.Collections.emptyList();
        String my_status;
        String my_role;
        Boolean my_ready;
        boolean can_set_ready,can_reply_offer,can_track;
        String offer_until;
        Tracking tracking;
        Integer ready_count;
        Integer team_size;
    }
}
