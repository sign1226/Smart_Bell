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
  contacts: Contact[];
  addContact: (contact: Contact) => void;
  removeContact: (id: string) => void;
  isRinging: boolean;
  setIsRinging: (ringing: boolean) => void;
  isRemoteOnline: boolean;
  setIsRemoteOnline: (online: boolean) => void;
  connectionError: string | null;
  setConnectionError: (error: string | null) => void;
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
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>([]);
  const [contacts, setContacts] = useState<Contact[]>(() => {
    const saved = localStorage.getItem('bell_contacts');
    return saved ? JSON.parse(saved) : [];
  });
  const [isRinging, setIsRinging] = useState(false);
  const [isRemoteOnline, setIsRemoteOnline] = useState(false);
  const [connectionError, setConnectionError] = useState<string | null>(null);

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

  const addHistory = useCallback((signal: CallSignal) => {
    setHistory(prev => [signal, ...prev].slice(0, 50));
  }, []);

  const addChatMessage = useCallback((msg: ChatMessage) => {
    setChatHistory(prev => [...prev, msg].slice(-100)); // Keep last 100 messages
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
      chatHistory, addChatMessage,
      contacts, addContact, removeContact,
      isRinging, setIsRinging: useCallback((ringing: boolean) => setIsRinging(ringing), []),
      isRemoteOnline, setIsRemoteOnline: useCallback((online: boolean) => setIsRemoteOnline(online), []),
      connectionError, setConnectionError: useCallback((error: string | null) => setConnectionError(error), [])
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
