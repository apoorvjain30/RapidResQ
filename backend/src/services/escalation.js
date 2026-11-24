import { inMemoryIncidents, inMemoryTeams, inMemoryTimeline, hydrateIncident } from '../routes/incidentRoutes.js';
import { logAudit } from './audit.js';
import { supabase } from '../config/supabase.js';

export function startEscalationMonitor(io) {
  console.log('[ESCALATION] Unacknowledged dispatch escalation monitor started (checking every 15s)...');

  setInterval(async () => {
    try {
      const now = Date.now();
      const timeoutMs = 60 * 1000; // 60 seconds unacknowledged timeout

      for (const incident of inMemoryIncidents.values()) {
        if (incident.status === 'ASSIGNED' && incident.assignedAt) {
          const elapsed = now - incident.assignedAt;

          if (elapsed >= timeoutMs) {
            console.log(`[ESCALATION] Incident ${incident.id} timed out after ${Math.round(elapsed / 1000)}s! Escalating back to VERIFIED.`);

            // Release assigned team in memory
            if (incident.assignedTeamId) {
              const team = inMemoryTeams.get(incident.assignedTeamId);
              if (team) {
                team.isAvailable = true;
              }

              if (supabase) {
                try {
                  await supabase
                    .from('response_teams')
                    .update({ is_available: true })
                    .eq('id', incident.assignedTeamId);
                } catch (tErr) {
                  console.error('[ESCALATION DB TEAM UPDATE ERROR]', tErr.message);
                }
              }
            }

            const previousTeamName = incident.assignedTeamName || 'Assigned Unit';

            // Reset incident back to VERIFIED
            incident.status = 'VERIFIED';
            incident.assignedTeamId = null;
            incident.assignedTeamName = null;
            incident.assignedAt = null;
            incident.escalationCount = (incident.escalationCount || 0) + 1;

            if (supabase) {
              try {
                await supabase
                  .from('incidents')
                  .update({
                    status: 'VERIFIED',
                    assigned_team_id: null,
                    assigned_team_name: null,
                    assigned_at: null,
                    escalation_count: incident.escalationCount
                  })
                  .eq('id', incident.id);
              } catch (iErr) {
                console.error('[ESCALATION DB INCIDENT UPDATE ERROR]', iErr.message);
              }
            }

            // Append timeline
            const timeline = inMemoryTimeline.get(incident.id) || [];
            const timelineEntry = {
              timestamp: now,
              statusLabel: 'Dispatch Escalated',
              actor: 'SYSTEM',
              note: `${previousTeamName} unacknowledged after 60s. Auto-re-queued for dispatch.`
            };
            timeline.push(timelineEntry);
            inMemoryTimeline.set(incident.id, timeline);

            if (supabase) {
              try {
                await supabase.from('incident_timeline').insert([{
                  id: `TL-${now}-${Math.floor(Math.random() * 1000)}`,
                  incident_id: incident.id,
                  timestamp_label: new Date(now).toLocaleTimeString('en-IN', { timeZone: 'Asia/Kolkata' }),
                  status_label: 'Dispatch Escalated',
                  actor: 'SYSTEM',
                  note: `${previousTeamName} unacknowledged after 60s. Auto-re-queued for dispatch.`
                }]);
              } catch (tlErr) {
                console.error('[ESCALATION DB TIMELINE INSERT ERROR]', tlErr.message);
              }
            }

            // Audit log
            await logAudit(
              'SYSTEM',
              'DISPATCH_ESCALATED',
              incident.id,
              `${previousTeamName} unresponsive after 60s. Auto-re-queued for dispatch.`
            );

            const hydrated = hydrateIncident(incident);

            // Broadcast to staff Socket.IO room
            io.to('staff').emit('incident:escalated', {
              incidentId: incident.id,
              previousTeamName,
              message: `${previousTeamName} failed to acknowledge dispatch within 60s. Incident re-queued.`
            });

            io.to('staff').emit('incidents:updated', hydrated);

            // Broadcast to public tracking room
            const sanitized = { ...hydrated };
            delete sanitized.reporterContact;
            io.to(`track:${incident.id}`).emit('incident:updated', sanitized);
          }
        }
      }
    } catch (err) {
      console.error('[ESCALATION ERROR]', err.message);
    }
  }, 15000);
}
