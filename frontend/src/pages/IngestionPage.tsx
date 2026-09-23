import { useEffect, useMemo, useState } from 'react';
import { api } from '../services/api';
import { Search, Plus, Upload, Database, ClipboardCheck, X, Eye } from 'lucide-react';

type CaseItem = { id: string; caseNumber: string; title: string };
type Entity = {
  id: string;
  referenceCode: string;
  primaryName: string;
  entityType?: string;
  caseNumber?: string;
  caseTitle?: string;
  caseRole?: string;
  status?: string;
};

type DetailField = {
  key: string;
  label: string;
  placeholder?: string;
  type?: 'text' | 'date' | 'datetime-local';
};

const ENTITY_TYPES = [
  'PERSON',
  'PHONE',
  'EMAIL',
  'VEHICLE',
  'LOCATION',
  'ORGANIZATION',
  'BANK_ACCOUNT',
  'DOCUMENT',
  'EVENT',
];

const CASE_ROLES = [
  'SUSPECT',
  'VICTIM',
  'EYEWITNESS',
  'PERSON_OF_INTEREST',
  'INFORMANT',
  'COMPLAINANT',
  'ASSOCIATE',
  'EVIDENCE',
  'CRIME_LOCATION',
  'SEIZURE_LOCATION',
  'SUSPECT_ORGANIZATION',
  'UNKNOWN',
];

const CASE_ROLE_LABELS: Record<string, string> = {
  SUSPECT: 'Suspect',
  VICTIM: 'Victim',
  EYEWITNESS: 'Eyewitness',
  PERSON_OF_INTEREST: 'Person of Interest',
  INFORMANT: 'Informant',
  COMPLAINANT: 'Complainant',
  ASSOCIATE: 'Associate',
  EVIDENCE: 'Evidence',
  CRIME_LOCATION: 'Crime Location',
  SEIZURE_LOCATION: 'Seizure Location',
  SUSPECT_ORGANIZATION: 'Suspect Organization',
  UNKNOWN: 'Unknown',
};

const ENTITY_TYPE_HELP: Record<string, string> = {
  PERSON: 'Identity information for an individual person.',
  PHONE: 'Telephone identifier and subscriber/carrier information.',
  EMAIL: 'Electronic mail identifier and account ownership information.',
  VEHICLE: 'Vehicle registration, make/model, owner and appearance information.',
  LOCATION: 'Named location, address and geographic coordinates.',
  ORGANIZATION: 'Organization identity, registration and contact information.',
  BANK_ACCOUNT: 'Financial account identifier and holder/bank information.',
  DOCUMENT: 'Document identifier, type, issuing authority and issue date.',
  EVENT: 'Incident/event identity, category, date/time and place.',
};

const TYPE_FIELDS: Record<string, DetailField[]> = {
  PHONE: [
    { key: 'subscriber', label: 'Subscriber / owner', placeholder: 'Registered subscriber or associated person' },
    { key: 'carrier', label: 'Carrier', placeholder: 'Mobile/network provider' },
  ],
  EMAIL: [
    { key: 'owner', label: 'Account owner', placeholder: 'Associated person or organization' },
    { key: 'provider', label: 'Provider', placeholder: 'Email provider' },
  ],
  VEHICLE: [
    { key: 'makeModel', label: 'Make / model', placeholder: 'e.g. Mahindra Scorpio' },
    { key: 'color', label: 'Color', placeholder: 'e.g. White' },
    { key: 'owner', label: 'Registered / known owner', placeholder: 'Owner name' },
    { key: 'vehicleClass', label: 'Vehicle class', placeholder: 'e.g. SUV, sedan, truck' },
  ],
  LOCATION: [
    { key: 'address', label: 'Address / site details', placeholder: 'Street, building, landmark or locality' },
    { key: 'locationType', label: 'Location type', placeholder: 'e.g. warehouse, residence, seizure site' },
    { key: 'landmark', label: 'Landmark', placeholder: 'Nearby landmark' },
  ],
  ORGANIZATION: [
    { key: 'registrationNumber', label: 'Registration number', placeholder: 'Organization/company registration number' },
    { key: 'contactPerson', label: 'Primary contact', placeholder: 'Known representative or contact' },
    { key: 'organizationType', label: 'Organization type', placeholder: 'e.g. logistics company, association' },
  ],
  BANK_ACCOUNT: [
    { key: 'accountHolder', label: 'Account holder', placeholder: 'Name of account holder' },
    { key: 'bankName', label: 'Bank', placeholder: 'Bank name' },
    { key: 'ifsc', label: 'IFSC / branch code', placeholder: 'Bank/branch identifier' },
    { key: 'accountType', label: 'Account type', placeholder: 'e.g. savings, current' },
  ],
  DOCUMENT: [
    { key: 'documentType', label: 'Document type', placeholder: 'e.g. shipment manifest, FIR, ID document' },
    { key: 'issuingAuthority', label: 'Issuing authority', placeholder: 'Agency or organization that issued it' },
    { key: 'issueDate', label: 'Issue date', type: 'date' },
    { key: 'documentNumber', label: 'Document number', placeholder: 'Reference / registration number' },
  ],
  EVENT: [
    { key: 'eventType', label: 'Event type', placeholder: 'e.g. seizure, meeting, transfer' },
    { key: 'eventDateTime', label: 'Event date / time', type: 'datetime-local' },
    { key: 'eventLocation', label: 'Event location', placeholder: 'Where the event occurred' },
    { key: 'eventReference', label: 'Event reference', placeholder: 'Incident/FIR/log reference' },
  ],
};

const emptyEntityForm = {
  entityType: 'PERSON',
  caseRole: 'SUSPECT',
  primaryName: '',
  alias: '',
  dateOfBirth: '',
  gender: '',
  nationality: '',
  phone: '',
  email: '',
  description: '',
  confidence: '',
  locationLabel: '',
  locationLat: '',
  locationLng: '',
  sourceReference: '',
  details: {} as Record<string, string>,
};

function formatEntityType(type: string) {
  return type.replaceAll('_', ' ');
}

function buildDescription(form: typeof emptyEntityForm) {
  const detailLines = Object.entries(form.details)
    .filter(([, value]) => value.trim())
    .map(([key, value]) => {
      const field = TYPE_FIELDS[form.entityType]?.find((item) => item.key === key);
      return `${field?.label || key}: ${value.trim()}`;
    });

  const description = form.description.trim();
  if (!detailLines.length) return description;
  const structured = `ENTITY-SPECIFIC DETAILS\n${detailLines.join('\n')}`;
  return description ? `${description}\n\n${structured}` : structured;
}

export default function IngestionPage() {
  const [cases, setCases] = useState<CaseItem[]>([]);
  const [caseId, setCaseId] = useState('');
  const [sourceType, setSourceType] = useState('FIR');
  const [file, setFile] = useState<File>();
  const [notes, setNotes] = useState('');
  const [rows, setRows] = useState<any[]>([]);
  const [query, setQuery] = useState('');
  const [entityTypeFilter, setEntityTypeFilter] = useState('ALL');
  const [entities, setEntities] = useState<Entity[]>([]);
  const [selected, setSelected] = useState<Entity | null>(null);
  const [showNewEntity, setShowNewEntity] = useState(false);
  const [message, setMessage] = useState('');
  const [entityError, setEntityError] = useState('');

  const selectedCase = useMemo(() => cases.find((item) => item.id === caseId), [cases, caseId]);

  const loadCases = () =>
    api.get('/cases')
      .then((response) => {
        setCases(response.data);
        if (!caseId && response.data[0]) setCaseId(response.data[0].id);
      })
      .catch(() => setCases([]));

  const loadRows = () => {
    if (!caseId) {
      setRows([]);
      return;
    }
    void api.get('/ingestion', { params: { caseId } })
      .then((response) => setRows(response.data))
      .catch(() => setRows([]));
  };

  useEffect(() => { void loadCases(); }, []);
  useEffect(() => { loadRows(); }, [caseId]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (query.trim().length < 2) {
        setEntities([]);
        return;
      }
      const params: Record<string, string> = { query: query.trim() };
      if (entityTypeFilter !== 'ALL') params.type = entityTypeFilter;
      void api.get('/entities/search', { params })
        .then((response) => setEntities(response.data))
        .catch(() => setEntities([]));
    }, 250);
    return () => clearTimeout(timer);
  }, [query, entityTypeFilter]);

  const submitImport = async () => {
    if (!caseId) {
      setMessage('Select a case first.');
      return;
    }
    const form = new FormData();
    form.append('caseId', caseId);
    form.append('sourceType', sourceType);
    form.append('notes', notes);
    if (file) form.append('file', file);
    try {
      await api.post('/ingestion', form);
      setFile(undefined);
      setNotes('');
      setMessage('Source record imported and queued for review.');
      loadRows();
    } catch (error: any) {
      setMessage(error.response?.data?.message || 'Import failed.');
    }
  };

  const viewSourceFile = async (id: string, fileName: string) => {
    const opened = window.open('', '_blank');
    if (!opened) {
      setMessage('Please allow pop-ups to view the file.');
      return;
    }

    try {
      const response = await api.get(`/ingestion/${id}/file`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      opened.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error: any) {
      opened.close();
      setMessage(error.response?.data?.message || `Unable to open ${fileName}.`);
    }
  };

  const createEntity = async (form: typeof emptyEntityForm) => {
    setEntityError('');
    if (!caseId) {
      setEntityError('Select a case before creating an entity.');
      return;
    }

    const description = buildDescription(form);

    try {
      const response = await api.post('/entities', {
        caseId,
        entityType: form.entityType,
        primaryName: form.primaryName.trim(),
        alias: form.alias,
        caseRole: form.caseRole,
        dateOfBirth: form.entityType === 'PERSON' && form.dateOfBirth ? form.dateOfBirth : null,
        gender: form.entityType === 'PERSON' ? form.gender : '',
        nationality: form.entityType === 'PERSON' ? form.nationality : '',
        phone: form.entityType === 'PERSON' ? form.phone : '',
        email: form.entityType === 'PERSON' ? form.email : '',
        description,
        status: 'UNDER_INVESTIGATION',
        confidence: form.confidence === '' ? null : Number(form.confidence) / 100,
        locationLabel: form.locationLabel,
        locationLat: form.locationLat === '' ? null : Number(form.locationLat),
        locationLng: form.locationLng === '' ? null : Number(form.locationLng),
        sourceReference: form.sourceReference,
      });
      setSelected(response.data);
      setQuery('');
      setEntities([]);
      setShowNewEntity(false);
      setMessage(`Entity registered as ${response.data.referenceCode}.`);
    } catch (error: any) {
      setEntityError(error.response?.data?.message || 'Could not create entity.');
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <div className="eyebrow">DATA INGESTION</div>
          <h1>Import and register intelligence</h1>
          <p>Search authorized records, register investigation entities, and attach source material to a case.</p>
        </div>
      </div>

      <div className="ingestion-grid">
        <section className="panel">
          <div className="panel-head">
            <div>
              <h2>Entity lookup</h2>
              <small>Search existing entities before creating a new case-specific registration.</small>
            </div>
            <Search size={18} />
          </div>

          <div className="two">
            <label>
              Entity type
              <select value={entityTypeFilter} onChange={(event) => setEntityTypeFilter(event.target.value)}>
                <option value="ALL">All types</option>
                {ENTITY_TYPES.map((type) => <option key={type}>{formatEntityType(type)}</option>)}
              </select>
            </label>
            <label>
              Case
              <select value={caseId} onChange={(event) => setCaseId(event.target.value)}>
                <option value="">Select case</option>
                {cases.map((item) => <option key={item.id} value={item.id}>{item.caseNumber} · {item.title}</option>)}
              </select>
            </label>
          </div>

          <div className="search search-large">
            <Search size={16} />
            <input
              placeholder="Search entity name or identifier…"
              value={query}
              onChange={(event) => { setQuery(event.target.value); setSelected(null); }}
            />
          </div>

          {query.length >= 2 && entities.length > 0 && (
            <div className="search-results">
              {entities.map((entity) => (
                <button key={entity.id} onClick={() => setSelected(entity)}>
                  <span className="result-code">{entity.referenceCode}</span>
                  <span>
                    <b>{entity.primaryName}</b>
                    <small>{entity.entityType || 'ENTITY'} · {entity.caseRole || 'No case role'}</small>
                  </span>
                </button>
              ))}
            </div>
          )}

          {query.length >= 2 && entities.length === 0 && <div className="empty compact">No matching entity found.</div>}

          {selected && (
            <div className="selected-entity">
              <div>
                <span className="mono">{selected.referenceCode}</span>
                <b>{selected.primaryName}</b>
                <small>{selected.entityType || 'ENTITY'} · {selected.caseRole || 'No case role'} · {selected.status || 'ACTIVE'}</small>
              </div>
              <button className="ghost small" onClick={() => setSelected(null)}>Clear</button>
            </div>
          )}

          <button className="secondary-action" onClick={() => { setEntityError(''); setShowNewEntity(true); }} disabled={!caseId}>
            <Plus size={16} /> Add new entity
          </button>
          {!caseId && <div className="field-note">Select a case first to register an entity.</div>}
          {message && <div className="notice success">{message}</div>}
        </section>

        <section className="panel">
          <div className="panel-head">
            <div>
              <h2>Import source data</h2>
              <small>Original files are hashed and held for review.</small>
            </div>
            <Upload size={18} />
          </div>
          <label>Case<select value={caseId} onChange={(event) => setCaseId(event.target.value)}><option value="">Select case</option>{cases.map((item) => <option key={item.id} value={item.id}>{item.caseNumber} · {item.title}</option>)}</select></label>
          <label>Source type<select value={sourceType} onChange={(event) => setSourceType(event.target.value)}><option>FIR</option><option>CDR</option><option>FINANCIAL_TRANSACTION</option><option>SURVEILLANCE</option><option>SOCIAL_INTELLIGENCE</option><option>POLICE_REPORT</option><option>OTHER</option></select></label>
          <label>File<input type="file" accept=".pdf,.csv,.txt,.json,.png,.jpg,.jpeg" onChange={(event) => setFile(event.target.files?.[0])} /></label>
          <label>Notes<textarea value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Describe source, provenance or review notes…" /></label>
          <button className="primary" disabled={!caseId} onClick={submitImport}><Upload size={15} /> Import for review</button>
        </section>
      </div>

      <section className="panel table-panel">
        <div className="panel-head">
          <div><h2>Ingestion ledger</h2><small>Database-backed source imports for the selected case.</small></div>
          <ClipboardCheck size={18} />
        </div>
        {rows.length ? <div className="table-scroll"><table><thead><tr><th>FILE</th><th>SOURCE</th><th>RECORDS</th><th>STATUS</th><th>HASH</th><th>CREATED</th><th>ACTION</th></tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td>{row.fileName}</td><td>{row.sourceType}</td><td>{row.recordCount}</td><td><span className="pill amber">{row.status}</span></td><td className="mono">{row.sha256?.slice(0, 12) || '—'}</td><td>{new Date(row.createdAt).toLocaleString()}</td><td>{row.hasFile ? <button type="button" className="ghost small" onClick={() => void viewSourceFile(row.id, row.fileName)}><Eye size={13} /> View</button> : <span className="field-note">—</span>}</td></tr>)}</tbody></table></div> : <div className="empty">No source records imported for this case.</div>}
      </section>

      {showNewEntity && (
        <NewEntityModal
          caseLabel={selectedCase ? `${selectedCase.caseNumber} · ${selectedCase.title}` : 'Selected case'}
          error={entityError}
          onClose={() => setShowNewEntity(false)}
          onCreate={createEntity}
        />
      )}
    </div>
  );
}

function NewEntityModal({
  caseLabel,
  error,
  onClose,
  onCreate,
}: {
  caseLabel: string;
  error: string;
  onClose: () => void;
  onCreate: (form: typeof emptyEntityForm) => void;
}) {
  const [form, setForm] = useState({ ...emptyEntityForm, details: {} as Record<string, string> });
  const update = (key: keyof typeof emptyEntityForm, value: string) => setForm((current) => ({ ...current, [key]: value }));
  const updateDetail = (key: string, value: string) => setForm((current) => ({ ...current, details: { ...current.details, [key]: value } }));
  const isPerson = form.entityType === 'PERSON';
  const typeFields = TYPE_FIELDS[form.entityType] || [];

  const changeEntityType = (value: string) => {
    setForm((current) => ({
      ...current,
      entityType: value,
      alias: '',
      dateOfBirth: '',
      gender: '',
      nationality: '',
      phone: '',
      email: '',
      details: {},
    }));
  };

  return (
    <div className="modal-backdrop">
      <div className="modal wide-modal entity-registration-modal">
        <button className="modal-close" aria-label="Close" onClick={onClose}><X size={16} /></button>
        <div className="eyebrow">NEW ENTITY</div>
        <h2>Register investigation entity</h2>
        <p className="modal-subtitle">Choose the entity type first. The form changes to collect information relevant to that type. Every entity receives a unique 6-character identifier.</p>

        <label>Case<input value={caseLabel} readOnly /></label>
        <div className="two">
          <label>
            Entity type *
            <select value={form.entityType} onChange={(event) => changeEntityType(event.target.value)}>
              {ENTITY_TYPES.map((type) => <option key={type} value={type}>{formatEntityType(type)}</option>)}
            </select>
          </label>
          <label>
            Role in case *
            <select value={form.caseRole} onChange={(event) => update('caseRole', event.target.value)}>
              {CASE_ROLES.map((role) => <option key={role} value={role}>{CASE_ROLE_LABELS[role]}</option>)}
            </select>
          </label>
        </div>

        <div className="type-context-note">
          <strong>{formatEntityType(form.entityType)}</strong>
          <span>{ENTITY_TYPE_HELP[form.entityType]}</span>
        </div>

        <div className="two">
          <label>
            {isPerson ? 'Full name' : form.entityType === 'VEHICLE' ? 'Registration number' : form.entityType === 'PHONE' ? 'Phone number' : form.entityType === 'EMAIL' ? 'Email address' : form.entityType === 'BANK_ACCOUNT' ? 'Account identifier' : form.entityType === 'DOCUMENT' ? 'Document identifier' : form.entityType === 'EVENT' ? 'Event name' : 'Entity name / identifier'} *
            <input
              value={form.primaryName}
              onChange={(event) => update('primaryName', event.target.value)}
              placeholder={form.entityType === 'VEHICLE' ? 'e.g. MP04-XR-7284' : form.entityType === 'PHONE' ? '+91 98765 43210' : form.entityType === 'EMAIL' ? 'example@domain.test' : ''}
            />
          </label>
          <label>
            {form.entityType === 'VEHICLE' ? 'Make / model' : form.entityType === 'PHONE' ? 'Carrier' : form.entityType === 'EMAIL' ? 'Provider' : form.entityType === 'BANK_ACCOUNT' ? 'Bank' : form.entityType === 'DOCUMENT' ? 'Document type' : form.entityType === 'EVENT' ? 'Event category' : 'Alias'}
            <input value={form.alias} onChange={(event) => update('alias', event.target.value)} />
          </label>
        </div>

        {isPerson && <>
          <div className="two">
            <label>Date of birth<input type="date" value={form.dateOfBirth} onChange={(event) => update('dateOfBirth', event.target.value)} /></label>
            <label>Gender<select value={form.gender} onChange={(event) => update('gender', event.target.value)}><option value="">No information</option><option>Male</option><option>Female</option><option>Other</option></select></label>
          </div>
          <div className="two">
            <label>Phone<input value={form.phone} onChange={(event) => update('phone', event.target.value)} /></label>
            <label>Email<input value={form.email} onChange={(event) => update('email', event.target.value)} /></label>
          </div>
          <label>Nationality<input value={form.nationality} onChange={(event) => update('nationality', event.target.value)} /></label>
        </>}

        {typeFields.length > 0 && <>
          <div className="entity-form-section">
            <div className="eyebrow">TYPE-SPECIFIC INFORMATION</div>
            <p className="field-note">These fields are stored with the entity record as structured investigation details.</p>
          </div>
          <div className="two entity-type-fields">
            {typeFields.map((field) => (
              <label key={field.key}>
                {field.label}
                <input
                  type={field.type || 'text'}
                  value={form.details[field.key] || ''}
                  onChange={(event) => updateDetail(field.key, event.target.value)}
                  placeholder={field.placeholder}
                />
              </label>
            ))}
          </div>
        </>}

        <div className="entity-form-section">
          <div className="eyebrow">LOCATION & PROVENANCE</div>
        </div>
        <label>Location<input value={form.locationLabel} onChange={(event) => update('locationLabel', event.target.value)} placeholder="Location name or address" /></label>
        <div className="two"><label>Latitude<input value={form.locationLat} onChange={(event) => update('locationLat', event.target.value)} /></label><label>Longitude<input value={form.locationLng} onChange={(event) => update('locationLng', event.target.value)} /></label></div>
        <div className="two"><label>Confidence %<input type="number" min="0" max="100" value={form.confidence} onChange={(event) => update('confidence', event.target.value)} /></label><label>Source reference<input value={form.sourceReference} onChange={(event) => update('sourceReference', event.target.value)} /></label></div>
        <label>Description / notes<textarea value={form.description} onChange={(event) => update('description', event.target.value)} placeholder="Observations, provenance, verification notes, or other context…" /></label>
        {error && <div className="notice error">{error}</div>}
        <div className="modal-actions"><button className="ghost" onClick={onClose}>Cancel</button><button className="primary" disabled={!form.primaryName.trim() || !form.entityType || !form.caseRole} onClick={() => onCreate(form)}>Create entity</button></div>
      </div>
    </div>
  );
}
