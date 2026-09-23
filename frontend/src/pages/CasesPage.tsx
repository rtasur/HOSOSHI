import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import { CaseItem } from '../types';
import { useAuth } from '../auth/AuthContext';
import { Plus, Play, ArrowUpRight, SlidersHorizontal, UserPlus, X, Trash2, RotateCcw, Archive } from 'lucide-react';
import './cases-v19.css';

const PR = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
type AssignableUser = { id: string; username: string; fullName: string; roles: string[]; status: string };
type AssignRole = 'SUPERVISOR' | 'INVESTIGATOR' | 'ANALYST' | 'DATA_OPERATOR';

export default function CasesPage() {
  const { user } = useAuth();
  const [cases, setCases] = useState<CaseItem[]>([]);
  const [binCases, setBinCases] = useState<CaseItem[]>([]);
  const [sort, setSort] = useState('priority-desc');
  const [priority, setPriority] = useState('');
  const [modal, setModal] = useState(false);
  const [assignCase, setAssignCase] = useState<CaseItem | null>(null);
  const [assignable, setAssignable] = useState<AssignableUser[]>([]);
  const [selectedUser, setSelectedUser] = useState('');
  const [assignRole, setAssignRole] = useState<AssignRole>('SUPERVISOR');
  const [assignBusy, setAssignBusy] = useState(false);
  const [caseBusy, setCaseBusy] = useState<string | null>(null);
  const [form, setForm] = useState({ title: '', description: '', category: '', priority: 'MEDIUM' });
  const nav = useNavigate();

  const isAdmin = !!user?.roles?.includes('SUPER_ADMIN');
  const isSupervisor = !!user?.roles?.includes('INVESTIGATION_SUPERVISOR');
  const canCreate = isAdmin || isSupervisor;
  const canAssign = canCreate;

  const load = async () => {
    try {
      const r = await api.get('/cases', { params: { sort, priority } });
      setCases(r.data);
    } catch {
      setCases([]);
    }
  };

  const loadBin = async () => {
    if (!isAdmin) {
      setBinCases([]);
      return;
    }
    try {
      const r = await api.get('/cases/bin');
      setBinCases(r.data);
    } catch {
      setBinCases([]);
    }
  };

  useEffect(() => { void load(); }, [sort, priority]);
  useEffect(() => { void loadBin(); }, [isAdmin]);

  const loadAssignable = async () => {
    const r = await api.get('/users/assignable');
    const filtered = (r.data as AssignableUser[]).filter(x => {
      if (x.status && x.status !== 'APPROVED') return false;
      if (isSupervisor) return x.roles?.some(r => ['INVESTIGATOR', 'INTELLIGENCE_ANALYST', 'DATA_OPERATOR'].includes(r));
      return x.roles?.some(r => ['INVESTIGATION_SUPERVISOR', 'INVESTIGATOR', 'INTELLIGENCE_ANALYST', 'DATA_OPERATOR'].includes(r));
    });
    setAssignable(filtered);
    if (filtered.length) {
      setSelectedUser(filtered[0].id);
      setAssignRole(isSupervisor ? roleForUser(filtered[0], false) : roleForUser(filtered[0], true));
    } else {
      setSelectedUser('');
    }
  };

  const openAssign = async (c: CaseItem) => {
    setAssignCase(c);
    setSelectedUser('');
    setAssignRole(isSupervisor ? 'INVESTIGATOR' : 'SUPERVISOR');
    try { await loadAssignable(); } catch { setAssignable([]); }
  };

  const roleForUser = (target: AssignableUser, admin: boolean): AssignRole => {
    if (admin && target.roles.includes('INVESTIGATION_SUPERVISOR')) return 'SUPERVISOR';
    if (target.roles.includes('INVESTIGATOR')) return 'INVESTIGATOR';
    if (target.roles.includes('INTELLIGENCE_ANALYST')) return 'ANALYST';
    return 'DATA_OPERATOR';
  };

  const onAssignUserChange = (id: string) => {
    setSelectedUser(id);
    const target = assignable.find(x => x.id === id);
    if (target) setAssignRole(roleForUser(target, isAdmin));
  };

  const submitAssign = async () => {
    if (!assignCase || !selectedUser || assignBusy) return;
    setAssignBusy(true);
    try {
      await api.post(`/cases/${assignCase.id}/members`, { userId: selectedUser, memberRole: assignRole });
      setAssignCase(null);
      await load();
    } catch (e: any) {
      alert(e?.response?.data?.message || 'Unable to assign this case.');
    } finally { setAssignBusy(false); }
  };

  const deleteCase = async (c: CaseItem) => {
    if (!isAdmin || caseBusy) return;
    const confirmed = window.confirm(`Move "${c.title}" (${c.caseNumber}) to Bin?\n\nThe case will be hidden from normal case lists and preserved for restoration by an administrator.`);
    if (!confirmed) return;
    setCaseBusy(c.id);
    try {
      await api.delete(`/cases/${c.id}`);
      await Promise.all([load(), loadBin()]);
    } catch (e: any) {
      alert(e?.response?.data?.message || 'Unable to move this case to Bin.');
    } finally { setCaseBusy(null); }
  };

  const restoreCase = async (c: CaseItem) => {
    if (!isAdmin || caseBusy) return;
    setCaseBusy(c.id);
    try {
      await api.post(`/cases/${c.id}/restore`);
      await Promise.all([load(), loadBin()]);
    } catch (e: any) {
      alert(e?.response?.data?.message || 'Unable to restore this case.');
    } finally { setCaseBusy(null); }
  };

  const counts = useMemo(() => PR.reduce((m, p) => ({ ...m, [p]: cases.filter(c => c.priority === p).length }), {} as Record<string, number>), [cases]);
  const total = cases.length;

  const create = async () => {
    if (!form.title.trim() || !canCreate) return;
    try {
      const r = await api.post('/cases', { ...form, status: 'OPEN', classification: 'RESTRICTED' });
      setModal(false);
      setForm({ title: '', description: '', category: '', priority: 'MEDIUM' });
      nav('/cases/' + r.data.id);
    } catch (e: any) {
      alert(e?.response?.data?.message || 'You do not have permission to create a case.');
    }
  };

  const [investigatingId, setInvestigatingId] = useState<string | null>(null);

  const investigate = async (id: string) => {
    if (investigatingId) return;
    setInvestigatingId(id);
    try {
      const response = await api.post('/sessions/start', null, { params: { caseId: id } });
      if (!response.data?.active || !response.data?.caseId) throw new Error('The investigation session was not activated.');
      nav('/network');
    } catch (e: any) {
      const message = e?.response?.data?.message || e?.response?.data?.error || e?.message || 'Unable to start investigation.';
      alert(message);
    } finally { setInvestigatingId(null); }
  };

  return <div>
    <div className="page-head">
      <div><div className="eyebrow">CASE MANAGEMENT</div><h1>Investigation cases</h1><p>Only authorized database records are shown. An active investigation appears in the sidebar only after you start investigating.</p></div>
      {canCreate && <button className="primary" onClick={() => setModal(true)}><Plus size={15} /> New case</button>}
    </div>

    <section className="panel case-overview"><div className="panel-head"><div><h2>Case priority distribution</h2><small>{total} total visible cases</small></div><div className="mono">LIVE FROM DATABASE</div></div><div className="priority-chart">{PR.map(p => { const n = counts[p] || 0; const pct = total ? Math.round(n / total * 100) : 0; return <div className="priority-bar" key={p}><div className="priority-bar-label"><span>{p}</span><b>{n}</b></div><div className="priority-track"><i style={{ width: `${pct}%` }} /></div></div>; })}</div></section>

    <div className="toolbar"><div className="field-inline"><SlidersHorizontal size={15} /><select value={sort} onChange={e => setSort(e.target.value)}><option value="priority-desc">Priority: high → low</option><option value="priority-asc">Priority: low → high</option><option value="newest">Newest</option><option value="oldest">Oldest</option><option value="updated">Recently updated</option></select></div><select value={priority} onChange={e => setPriority(e.target.value)}><option value="">All priorities</option>{PR.map(x => <option key={x}>{x}</option>)}</select></div>

    {cases.length ? <div className="case-grid">{cases.map(c => <div className="case-card" key={c.id}>
      <div className="case-top"><span className={'priority ' + c.priority.toLowerCase()}>{c.priority}</span><span className="status">{c.status.replace(/_/g, ' ')}</span></div>
      <h3>{c.title}</h3><div className="mono">{c.caseNumber}</div><p>{c.description || 'No description provided.'}</p>
      <div className="case-meta"><span>{c.classification}</span><span>{new Date(c.createdAt).toLocaleString()}</span></div>
      <div className="case-actions">
        <Link className="ghost" to={'/cases/' + c.id}><ArrowUpRight size={13} /> Open Case</Link>
        {canAssign && <button className="ghost" onClick={() => void openAssign(c)}><UserPlus size={13} /> Assign Case</button>}
        <button className="primary small" disabled={!!investigatingId} onClick={() => void investigate(c.id)}><Play size={13} /> {investigatingId === c.id ? 'Opening…' : 'Investigate'}</button>
        {isAdmin && <button className="danger small" disabled={caseBusy === c.id} onClick={() => void deleteCase(c)}><Trash2 size={13} /> {caseBusy === c.id ? 'Moving…' : 'Delete'}</button>}
      </div>
    </div>)}</div> : <div className="empty large">No investigation cases visible to this account.</div>}

    {isAdmin && <section className="panel bin-panel">
      <div className="panel-head"><div><h2><Archive size={17}/> Bin</h2><small>Deleted cases are preserved here and can be restored by an administrator.</small></div><span className="mono">{binCases.length} DELETED</span></div>
      {binCases.length ? <div className="bin-grid">{binCases.map(c => <div className="bin-card" key={c.id}><div className="case-top"><span className={'priority ' + c.priority.toLowerCase()}>{c.priority}</span><span className="status">DELETED</span></div><h3>{c.title}</h3><div className="mono">{c.caseNumber}</div><p>{c.description || 'No description provided.'}</p><div className="case-meta"><span>{c.category || 'Uncategorized'}</span><span>{c.deletedAt ? new Date(c.deletedAt).toLocaleString() : 'Deleted'}</span></div><div className="case-actions"><button className="primary small" disabled={caseBusy === c.id} onClick={() => void restoreCase(c)}><RotateCcw size={13}/> {caseBusy === c.id ? 'Restoring…' : 'Restore'}</button></div></div>)}</div> : <div className="empty">Bin is empty.</div>}
    </section>}

    {modal && <div className="modal-backdrop"><div className="modal"><div className="eyebrow">NEW CASE</div><h2>Create investigation case</h2><label>Title<input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></label><label>Category<input value={form.category} onChange={e => setForm({ ...form, category: e.target.value })} /></label><label>Priority<select value={form.priority} onChange={e => setForm({ ...form, priority: e.target.value })}>{PR.map(p => <option key={p}>{p}</option>)}</select></label><label>Description<textarea value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label><div className="modal-actions"><button className="ghost" onClick={() => setModal(false)}>Cancel</button><button className="primary" disabled={!form.title.trim()} onClick={() => void create()}>Create case</button></div></div></div>}

    {assignCase && <div className="modal-backdrop"><div className="modal">
      <div className="modal-headline"><div><div className="eyebrow">CASE ASSIGNMENT</div><h2>Assign case</h2></div><button className="icon-btn" onClick={() => setAssignCase(null)} aria-label="Close assignment"><X size={16} /></button></div>
      <p><b>{assignCase.title}</b><br /><span className="mono">{assignCase.caseNumber}</span></p>
      <label>Assign to<select value={selectedUser} onChange={e => onAssignUserChange(e.target.value)}>{assignable.length ? assignable.map(x => <option key={x.id} value={x.id}>{x.fullName} · {x.username}</option>) : <option value="">No eligible users available</option>}</select></label>
      <label>Case role<select value={assignRole} onChange={e => setAssignRole(e.target.value as AssignRole)}>{isAdmin && <option value="SUPERVISOR">SUPERVISOR</option>}<option value="INVESTIGATOR">INVESTIGATOR</option><option value="ANALYST">ANALYST</option><option value="DATA_OPERATOR">DATA OPERATOR</option></select></label>
      <div className="notice"><span>AUTHORITY</span><span>{isSupervisor ? 'Supervisors may assign authorized Investigators, Analysts or Data Operators.' : 'Super Admins may assign Supervisors, Investigators, Analysts or Data Operators.'}</span></div>
      <div className="modal-actions"><button className="ghost" onClick={() => setAssignCase(null)}>Cancel</button><button className="primary" disabled={!selectedUser || assignBusy} onClick={() => void submitAssign()}>{assignBusy ? 'Assigning…' : 'Assign case'}</button></div>
    </div></div>}
  </div>;
}
