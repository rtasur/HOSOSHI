import { useEffect, useState } from 'react';
import { api } from '../services/api';
import { Check, UserX, Users, Lock, RefreshCw } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';

const ROLES = ['SUPER_ADMIN', 'INVESTIGATION_SUPERVISOR', 'INVESTIGATOR', 'INTELLIGENCE_ANALYST', 'DATA_OPERATOR', 'AUDITOR'];

export default function AdminPage() {
  const { user } = useAuth();
  const [pending, setPending] = useState<any[]>([]);
  const [users, setUsers] = useState<any[]>([]);
  const [cases, setCases] = useState<any[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setBusy(true);
    setError('');
    try {
      const r = await api.get('/users/admin/overview');
      setPending(r.data.pending ?? []);
      setUsers(r.data.users ?? []);
      setCases(r.data.cases ?? []);
    } catch (e: any) {
      setError(e.response?.data?.message || e.response?.data?.error || 'Unable to load administrator data.');
      setPending([]);
      setUsers([]);
      setCases([]);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => { void load(); }, []);

  if (!user?.roles.includes('SUPER_ADMIN')) {
    return <div className="empty large">Administrator privileges required.</div>;
  }

  const act = async (id: string, action: 'approve' | 'reject' | 'suspend') => {
    try {
      await api.post(`/users/admin/${id}/${action}`);
      await load();
    } catch (e: any) {
      setError(e.response?.data?.message || e.response?.data?.error || `Unable to ${action} user.`);
    }
  };

  const changeRole = async (id: string, role: string) => {
    try {
      await api.patch(`/users/admin/${id}/role`, { role });
      await load();
    } catch (e: any) {
      setError(e.response?.data?.message || e.response?.data?.error || 'Unable to update user role.');
    }
  };

  return <div>
    <div className="page-head">
      <div>
        <div className="eyebrow">ADMINISTRATION</div>
        <h1>Administration console</h1>
        <p>Approve registrations, manage global roles, and inspect who can access which investigations.</p>
      </div>
      <button className="ghost" onClick={() => void load()} disabled={busy}>
        <RefreshCw size={15} /> {busy ? 'Refreshing…' : 'Refresh'}
      </button>
    </div>

    {error && <div className="notice error" role="alert">{error}</div>}

    <div className="admin-summary">
      <div className="metric compact-metric"><div className="metric-label">PENDING</div><div className="metric-value">{pending.length}</div><div className="metric-note">Awaiting approval</div></div>
      <div className="metric compact-metric"><div className="metric-label">USERS</div><div className="metric-value">{users.length}</div><div className="metric-note">Registered accounts</div></div>
      <div className="metric compact-metric"><div className="metric-label">CASES</div><div className="metric-value">{cases.length}</div><div className="metric-note">All investigations</div></div>
    </div>

    <div className="admin-grid">
      <section className="panel">
        <div className="panel-head">
          <div><h2>Pending registrations</h2><small>{pending.length} awaiting review</small></div>
          <Users size={18} />
        </div>
        {pending.length ? pending.map(x => <div className="admin-row" key={x.id}>
          <div>
            <b>{x.fullName}</b>
            <small>{x.username} · {x.email || 'No email'} · requested {x.requestedRole || 'UNKNOWN'}</small>
          </div>
          <div className="row-actions">
            <button className="primary small" onClick={() => void act(x.id, 'approve')}><Check size={13} /> Approve</button>
            <button className="ghost small" onClick={() => void act(x.id, 'reject')}><UserX size={13} /> Reject</button>
          </div>
        </div>) : <div className="empty">No pending registrations.</div>}
      </section>

      <section className="panel">
        <div className="panel-head">
          <div><h2>Access directory</h2><small>All registered users and case-access overview</small></div>
          <Lock size={18} />
        </div>
        {users.length ? users.map(x => <div className="admin-row admin-user" key={x.id}>
          <div className="admin-user-main">
            <b>{x.fullName}</b>
            <small>{x.username} · {x.status} · {x.accessibleCases?.length || 0} access entries</small>
            <div className="role-tags">{x.roles.map((r: string) => <span key={r}>{r.replace(/_/g, ' ')}</span>)}</div>
            <small className="access-list">{x.accessibleCases?.join(' · ') || 'No case access'}</small>
          </div>
          <select className="role-select" value={x.roles?.[0] || ''} onChange={e => void changeRole(x.id, e.target.value)} disabled={x.username === user.username}>
            {ROLES.map(r => <option key={r}>{r}</option>)}
          </select>
        </div>) : <div className="empty">No registered accounts found.</div>}
      </section>
    </div>
  </div>;
}
