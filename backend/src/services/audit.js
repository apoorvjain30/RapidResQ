import { supabase } from '../config/supabase.js';

export function formatIstTime(date = new Date()) {
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: 'Asia/Kolkata',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true
  }).format(date);
}

export async function logAudit(actorRole, action, incidentId = 'SYSTEM', details = '') {
  const auditId = `AUD-${Math.floor(10000 + Math.random() * 90000)}`;
  const now = new Date();
  const timestampLabel = formatIstTime(now);

  const auditRow = {
    id: auditId,
    timestamp_label: timestampLabel,
    actor_role: actorRole,
    action: action,
    incident_id: incidentId,
    details: details,
    created_at: now.toISOString()
  };

  console.log(`[AUDIT] [${timestampLabel}] ${actorRole} | ${action} | ${incidentId} | ${details}`);

  if (supabase) {
    try {
      const { data, error } = await supabase
        .from('audit_logs')
        .insert([auditRow])
        .select()
        .maybeSingle();

      if (error) {
        console.error('[AUDIT SUPABASE ERROR]', error.message);
      } else if (data) {
        return data;
      }
    } catch (err) {
      console.error('[AUDIT ERROR]', err.message);
    }
  }

  return auditRow;
}
