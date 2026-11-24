import bcrypt from 'bcryptjs';
import { supabase } from './supabase.js';

export const defaultAccounts = [
  {
    email: 'admin@rapidresq.in',
    password: 'Admin@123',
    name: 'System Admin',
    role: 'ADMIN',
    department: 'Governance',
    assigned_team_id: null
  },
  {
    email: 'dispatch@rapidresq.in',
    password: 'Dispatch@123',
    name: 'Control Dispatcher',
    role: 'DISPATCHER',
    department: 'Command Center',
    assigned_team_id: null
  },
  {
    email: 'alpha@rapidresq.in',
    password: 'Unit@123',
    name: 'Unit Alpha Lead',
    role: 'RESPONDER',
    department: 'Medical EMS',
    assigned_team_id: 'TEAM-ALPHA'
  },
  {
    email: 'bravo@rapidresq.in',
    password: 'Unit@123',
    name: 'Unit Bravo Lead',
    role: 'RESPONDER',
    department: 'Fire & Hazmat',
    assigned_team_id: 'TEAM-BRAVO'
  },
  {
    email: 'citizen@rapidresq.in',
    password: 'Citizen@123',
    name: 'Apoorv Citizen',
    role: 'CITIZEN',
    department: 'Public',
    assigned_team_id: null
  }
];

// In-memory user store fallback for development when Supabase database is not connected
export const inMemoryUsers = new Map();

export async function seedDefaultUsers() {
  console.log('[SEED] Seeding default accounts...');

  for (const account of defaultAccounts) {
    const passwordHash = await bcrypt.hash(account.password, 10);
    const userRecord = {
      id: `USR-${account.role.toLowerCase()}-${account.email.split('@')[0]}`,
      email: account.email,
      password_hash: passwordHash,
      name: account.name,
      role: account.role,
      department: account.department,
      assigned_team_id: account.assigned_team_id,
      mfa_enabled: account.role !== 'CITIZEN',
      is_active: true,
      failed_attempts: 0,
      locked_until: null,
      created_at: new Date().toISOString()
    };

    inMemoryUsers.set(account.email.toLowerCase(), userRecord);

    if (supabase) {
      try {
        const { data: existing } = await supabase
          .from('users')
          .select('id')
          .eq('email', account.email)
          .maybeSingle();

        if (!existing) {
          const { error } = await supabase.from('users').insert([userRecord]);
          if (error) {
            console.error(`[SEED ERROR] Could not seed user ${account.email}:`, error.message);
          } else {
            console.log(`[SEED] Seeded account ${account.email} (${account.role})`);
          }
        }
      } catch (err) {
        console.error(`[SEED ERROR] Exception for ${account.email}:`, err.message);
      }
    }
  }

  console.log('[SEED] Default user accounts ready.');
}
