import React, { useState } from 'react';
import { useApp, type Contact } from '../context/AppContext';
import { User, Trash2, Edit2, Plus, ArrowLeft, Save, X } from 'lucide-react';

interface ContactsViewProps {
    onBack: () => void;
}

export const ContactsView: React.FC<ContactsViewProps> = ({ onBack }) => {
    const { contacts, addContact, removeContact, onlineStatuses } = useApp();
    const [isAdding, setIsAdding] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [formData, setFormData] = useState({ id: '', name: '' });

    const handleSave = () => {
        if (!formData.id.trim() || !formData.name.trim()) return;
        addContact({ id: formData.id, name: formData.name });
        setFormData({ id: '', name: '' });
        setIsAdding(false);
        setEditingId(null);
    };

    const startEdit = (contact: Contact) => {
        setFormData(contact);
        setEditingId(contact.id);
        setIsAdding(true);
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%', backgroundColor: '#000', color: '#fff', padding: '16px' }}>
            <header style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
                <button
                    onClick={onBack}
                    style={{ padding: '8px', backgroundColor: 'transparent', borderRadius: '50%', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    onMouseOver={e => (e.currentTarget.style.backgroundColor = '#111')}
                    onMouseOut={e => (e.currentTarget.style.backgroundColor = 'transparent')}
                >
                    <ArrowLeft size={24} />
                </button>
                <h1 style={{ fontSize: '1.25rem', fontWeight: 'bold', margin: 0 }}>連絡先の管理</h1>
                <button
                    onClick={() => { setIsAdding(true); setEditingId(null); setFormData({ id: '', name: '' }); }}
                    style={{ marginLeft: 'auto', padding: '8px', backgroundColor: '#2563eb', borderRadius: '50%', border: 'none', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                >
                    <Plus size={20} />
                </button>
            </header>

            {isAdding && (
                <div style={{ backgroundColor: '#111', padding: '16px', borderRadius: '12px', marginBottom: '24px', border: '1px solid #333' }}>
                    <h2 style={{ fontSize: '0.875rem', fontWeight: 'bold', color: '#9ca3af', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        {editingId ? '連絡先の編集' : '新しい連絡先を追加'}
                    </h2>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        <div>
                            <label style={{ display: 'block', fontSize: '0.75rem', color: '#6b7280', marginBottom: '4px' }}>デバイスID</label>
                            <input
                                type="text"
                                value={formData.id}
                                disabled={!!editingId}
                                onChange={e => setFormData({ ...formData, id: e.target.value })}
                                placeholder="デバイスIDを貼り付けてください"
                                style={{
                                    width: '100%',
                                    backgroundColor: '#000',
                                    border: '1px solid #333',
                                    borderRadius: '8px',
                                    padding: '8px 12px',
                                    fontSize: '0.875rem',
                                    color: '#fff',
                                    outline: 'none',
                                    opacity: editingId ? 0.5 : 1
                                }}
                            />
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '0.75rem', color: '#6b7280', marginBottom: '4px' }}>名前</label>
                            <input
                                type="text"
                                value={formData.name}
                                onChange={e => setFormData({ ...formData, name: e.target.value })}
                                placeholder="例: 友だちの名前"
                                style={{
                                    width: '100%',
                                    backgroundColor: '#000',
                                    border: '1px solid #333',
                                    borderRadius: '8px',
                                    padding: '8px 12px',
                                    fontSize: '0.875rem',
                                    color: '#fff',
                                    outline: 'none'
                                }}
                            />
                        </div>
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button
                                onClick={handleSave}
                                style={{
                                    flex: 1,
                                    backgroundColor: '#2563eb',
                                    color: '#fff',
                                    padding: '10px',
                                    borderRadius: '8px',
                                    fontSize: '0.875rem',
                                    fontWeight: 'bold',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: '8px'
                                }}
                            >
                                <Save size={16} /> 保存
                            </button>
                            <button
                                onClick={() => setIsAdding(false)}
                                style={{
                                    flex: 1,
                                    backgroundColor: '#374151',
                                    color: '#fff',
                                    padding: '10px',
                                    borderRadius: '8px',
                                    fontSize: '0.875rem',
                                    fontWeight: 'bold',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: '8px'
                                }}
                            >
                                <X size={16} /> キャンセル
                            </button>
                        </div>
                    </div>
                </div>
            )}

            <div style={{ flex: 1, overflowY: 'auto' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {contacts.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '80px 0', color: '#6b7280' }}>
                            <User size={48} style={{ margin: '0 auto 16px', opacity: 0.2 }} />
                            <p>連絡先がまだ登録されていません</p>
                        </div>
                    ) : (
                        contacts.map(contact => (
                            <div key={contact.id} style={{ backgroundColor: '#111', border: '1px solid #1f2937', padding: '12px', borderRadius: '12px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                                <div style={{ width: '40px', height: '40px', borderRadius: '50%', backgroundColor: 'rgba(30, 58, 138, 0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#60a5fa', fontWeight: 'bold', position: 'relative' }}>
                                    {contact.name[0].toUpperCase()}
                                    <div style={{
                                        position: 'absolute',
                                        bottom: 0,
                                        right: 0,
                                        width: '12px',
                                        height: '12px',
                                        borderRadius: '50%',
                                        backgroundColor: onlineStatuses.get(contact.id) ? '#22c55e' : '#9ca3af',
                                        border: '2px solid #111'
                                    }} />
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{ fontWeight: 'bold', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{contact.name}</div>
                                    <div style={{ fontSize: '10px', color: '#6b7280', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {contact.id}
                                        {onlineStatuses.get(contact.id) && <span style={{ marginLeft: '8px', color: '#22c55e' }}>Online</span>}
                                    </div>
                                </div>
                                <div style={{ display: 'flex', gap: '4px' }}>
                                    <button
                                        onClick={() => startEdit(contact)}
                                        style={{ padding: '8px', color: '#9ca3af', backgroundColor: 'transparent', borderRadius: '8px' }}
                                        onMouseOver={e => (e.currentTarget.style.color = '#fff')}
                                        onMouseOut={e => (e.currentTarget.style.color = '#9ca3af')}
                                    >
                                        <Edit2 size={16} />
                                    </button>
                                    <button
                                        onClick={() => { if (confirm('連絡先を削除しますか？')) removeContact(contact.id); }}
                                        style={{ padding: '8px', color: '#f87171', backgroundColor: 'transparent', borderRadius: '8px' }}
                                        onMouseOver={e => (e.currentTarget.style.backgroundColor = 'rgba(127, 29, 29, 0.2)')}
                                        onMouseOut={e => (e.currentTarget.style.backgroundColor = 'transparent')}
                                    >
                                        <Trash2 size={16} />
                                    </button>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
};
