import crypto from 'crypto';
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

  let prevHash = 'GENESIS';

  if (supabase) {
    try {
      const { data, error } = await supabase
        .from('audit_logs')
        .select('record_hash')
        .order('created_at', { ascending: false })
        .order('id', { ascending: false })
        .limit(1);

      if (!error && data && data.length > 0 && data[0].record_hash) {
        prevHash = data[0].record_hash;
      }
    } catch (e) {
      // Fallback prevHash
    }
  }

  const rawString = `${prevHash}|${auditId}|${actorRole}|${action}|${incidentId}|${details}`;
  const recordHash = crypto.createHash('sha256').update(rawString).digest('hex');

  const auditRow = {
    id: auditId,
    timestamp_label: timestampLabel,
    actor_role: actorRole,
    action: action,
    incident_id: incidentId,
    details: details,
    prev_hash: prevHash,
    record_hash: recordHash,
    created_at: now.toISOString()
  };

  console.log(`[AUDIT] [${timestampLabel}] ${actorRole} | ${action} | ${incidentId} | Hash: ${recordHash.substring(0, 10)}...`);

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

export async function verifyAuditChain() {
  if (!supabase) {
    return { valid: true, totalLogs: 0, checkedChainedLogs: 0, tamperedIds: [], latestHash: null, message: 'Supabase not connected' };
  }

  try {
    const { data, error } = await supabase
      .from('audit_logs')
      .select('*')
      .order('created_at', { ascending: true })
      .order('id', { ascending: true });

    if (error || !data) {
      return { valid: false, totalLogs: 0, checkedChainedLogs: 0, tamperedIds: [], latestHash: null, error: error?.message || 'Failed to fetch audit logs' };
    }

    let valid = true;
    const tamperedIds = [];
    let checkedCount = 0;
    let expectedPrevHash = 'GENESIS';
    let latestHash = null;

    for (const row of data) {
      if (!row.record_hash) continue;
      checkedCount++;

      // 1. Verify prev_hash continuity
      if (checkedCount > 1 && row.prev_hash !== expectedPrevHash) {
        valid = false;
        tamperedIds.push(row.id);
      }

      // 2. Recompute record_hash
      const rawString = `${row.prev_hash || 'GENESIS'}|${row.id}|${row.actor_role}|${row.action}|${row.incident_id}|${row.details}`;
      const recomputedHash = crypto.createHash('sha256').update(rawString).digest('hex');

      if (recomputedHash !== row.record_hash) {
        valid = false;
        if (!tamperedIds.includes(row.id)) tamperedIds.push(row.id);
      }

      expectedPrevHash = row.record_hash;
      latestHash = row.record_hash;
    }

    return {
      valid,
      totalLogs: data.length,
      checkedChainedLogs: checkedCount,
      tamperedIds,
      latestHash
    };
  } catch (err) {
    return { valid: false, totalLogs: 0, checkedChainedLogs: 0, tamperedIds: [], latestHash: null, error: err.message };
  }
}
