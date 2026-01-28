import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import IncomingCall from '../plugins/IncomingCall';

export type AppMode = 'sender' | 'receiver' | 'settings';

interface MqttConfig {
  host: string;
  port: number;
  clientId: string;
  topic: string;
}

interface CallSignal {
  from: string;
  cmd: 'call';
  priority: 'normal' | 'high';
  timestamp: number;
}

export interface Contact {
  id: string;
  name: string;
}

export interface ChatMessage {
  id: string;
  from: string;
  fromId: string;
  text: string;
  timestamp: number;
  isSelf: boolean;
  isDelivered?: boolean;
}

interface AppState {
  mode: AppMode;
  setMode: (mode: AppMode) => void;
  config: MqttConfig;
  setConfig: (config: MqttConfig) => void;
  deviceId: string;
  isConnected: boolean;
  setIsConnected: (connected: boolean) => void;
  history: CallSignal[];
  addHistory: (signal: CallSignal) => void;
  chatHistory: ChatMessage[];
  addChatMessage: (msg: ChatMessage) => void;
  updateChatMessage: (id: string, updates: Partial<ChatMessage>) => void;
  clearChatHistory: () => void;
  contacts: Contact[];
  addContact: (contact: Contact) => void;
  removeContact: (id: string) => void;
  isRinging: boolean;
  setIsRinging: (ringing: boolean) => void;
  isRemoteOnline: boolean;
  setIsRemoteOnline: (online: boolean) => void;
  connectionError: string | null;
  setConnectionError: (error: string | null) => void;
  onlineStatuses: Map<string, boolean>;
  setOnlineStatuses: React.Dispatch<React.SetStateAction<Map<string, boolean>>>;
  callStatus: 'idle' | 'sending' | 'delivered' | 'failed';
  setCallStatus: React.Dispatch<React.SetStateAction<'idle' | 'sending' | 'delivered' | 'failed'>>;
  defaultRecipientId: string;
  setDefaultRecipientId: (id: string) => void;
}

const AppContext = createContext<AppState | undefined>(undefined);

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mode, setMode] = useState<AppMode>(() => {
    return (localStorage.getItem('bell_mode') as AppMode) || 'sender';
  });

  const [deviceId] = useState<string>(() => {
    let id = localStorage.getItem('bell_device_id');
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem('bell_device_id', id);
    }
    return id;
  });

  const [config, setConfig] = useState<MqttConfig>(() => {
    const saved = localStorage.getItem('bell_config');
    return saved ? JSON.parse(saved) : {
      host: 'localhost',
      port: 8083, // WebSocket port for MQTT
      clientId: `bell_${Math.random().toString(16).slice(2, 8)}`,
      topic: 'smartbell/call'
    };
  });

  const [isConnected, setIsConnected] = useState(false);
  const [history, setHistory] = useState<CallSignal[]>([]);
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>(() => {
    const saved = localStorage.getItem('bell_chat_history');
    return saved ? JSON.parse(saved) : [];
  });
  const [contacts, setContacts] = useState<Contact[]>(() => {
    const saved = localStorage.getItem('bell_contacts');
    return saved ? JSON.parse(saved) : [];
  });
  const [isRinging, setIsRinging] = useState(false);
  const [isRemoteOnline, setIsRemoteOnline] = useState(false);
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const [onlineStatuses, setOnlineStatuses] = useState<Map<string, boolean>>(new Map());
  const [callStatus, setCallStatus] = useState<'idle' | 'sending' | 'delivered' | 'failed'>('idle');
  const [defaultRecipientId, setDefaultRecipientId] = useState<string>(() => {
    return localStorage.getItem('bell_default_recipient') || '';
  });

  useEffect(() => {
    localStorage.setItem('bell_mode', mode);
  }, [mode]);

  useEffect(() => {
    localStorage.setItem('bell_config', JSON.stringify(config));
  }, [config]);

  useEffect(() => {
    localStorage.setItem('bell_contacts', JSON.stringify(contacts));
    // Sync to Native for Widgets
    IncomingCall.syncContacts({ contacts: JSON.stringify(contacts) }).catch(console.error);
  }, [contacts]);

  useEffect(() => {
    localStorage.setItem('bell_chat_history', JSON.stringify(chatHistory));
  }, [chatHistory]);

  useEffect(() => {
    localStorage.setItem('bell_default_recipient', defaultRecipientId);
  }, [defaultRecipientId]);

  const addChatMessage = useCallback((msg: ChatMessage) => {
    setChatHistory(prev => {
      // Duplicate prevention
      if (prev.some(m => m.id === msg.id)) return prev;
      return [...prev, msg].slice(-200);
    });
  }, []);

  const updateChatMessage = useCallback((id: string, updates: Partial<ChatMessage>) => {
    setChatHistory(prev => prev.map(msg => msg.id === id ? { ...msg, ...updates } : msg));
  }, []);

  const clearChatHistory = useCallback(() => {
    if (window.confirm('チャット履歴をすべて削除しますか？')) {
      setChatHistory([]);
      localStorage.removeItem('bell_chat_history');
    }
  }, []);

  // Sync background messages from Native on startup and periodically
  useEffect(() => {
    const syncPendingMessages = async () => {
      try {
        const { messages } = await IncomingCall.getPendingChatMessages();
        if (messages && messages.length > 0) {
          console.log(`Synced ${messages.length} messages from Native storage`);
          messages.forEach(msgPayload => {
            const msg: ChatMessage = {
              id: msgPayload.id || crypto.randomUUID(),
              from: msgPayload.from || '誰か',
              fromId: msgPayload.fromId || '',
              text: msgPayload.text || '',
              timestamp: msgPayload.timestamp || Date.now(),
              isSelf: false
            };
            addChatMessage(msg);
          });
        }
      } catch (e) {
        console.error('Failed to sync pending messages:', e);
      }
    };

    // Initial sync
    syncPendingMessages();

    // Check periodically if app is in foreground
    const interval = setInterval(syncPendingMessages, 5000);
    return () => clearInterval(interval);
  }, [addChatMessage]);

  const addHistory = useCallback((signal: CallSignal) => {
    setHistory(prev => [signal, ...prev].slice(0, 50));
  }, []);

  const addContact = useCallback((contact: Contact) => {
    setContacts(prev => {
      const exists = prev.find(c => c.id === contact.id);
      if (exists) return prev.map(c => c.id === contact.id ? contact : c);
      return [...prev, contact];
    });
  }, []);

  const removeContact = useCallback((id: string) => {
    setContacts(prev => prev.filter(c => c.id !== id));
  }, []);

  return (
    <AppContext.Provider value={{
      mode, setMode,
      config, setConfig,
      deviceId,
      isConnected, setIsConnected: useCallback((connected: boolean) => setIsConnected(connected), []),
      history, addHistory,
      chatHistory, addChatMessage, updateChatMessage, clearChatHistory,
      contacts, addContact, removeContact,
      isRinging, setIsRinging: useCallback((ringing: boolean) => setIsRinging(ringing), []),
      isRemoteOnline, setIsRemoteOnline: useCallback((online: boolean) => setIsRemoteOnline(online), []),
      connectionError, setConnectionError: useCallback((error: string | null) => setConnectionError(error), []),
      onlineStatuses, setOnlineStatuses,
      callStatus, setCallStatus,
      defaultRecipientId, setDefaultRecipientId: (id: string) => setDefaultRecipientId(id)
    }}>
      {children}
    </AppContext.Provider>
  );
};

export const useApp = () => {
  const context = useContext(AppContext);
  if (!context) throw new Error('useApp must be used within AppProvider');
  return context;
};
