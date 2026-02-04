import React, { useState } from 'react';
import { AppProvider } from './context/AppContext';
import { SenderView } from './components/SenderView';
import { ChatView } from './components/ChatView';
import { SettingsView } from './components/SettingsView';
import { useMqtt } from './hooks/useMqtt';
import { Phone, MessageSquare, Settings } from 'lucide-react';
import { PermissionGuide } from './components/PermissionGuide';

const AppContent: React.FC = () => {
  const { sendCall, sendChat } = useMqtt();
  const [activeTab, setActiveTab] = useState<'phone' | 'chat' | 'settings'>('phone');

  React.useEffect(() => {
    const handleOpenChat = () => {
      console.log('Open chat trigger received');
      setActiveTab('chat');
    };
    window.addEventListener('openChat', handleOpenChat);
    return () => {
      window.removeEventListener('openChat', handleOpenChat);
    };
  }, [sendCall]);

  const renderContent = () => {
    switch (activeTab) {
      case 'phone':
        return <SenderView sendCall={sendCall} />;
      case 'chat':
        return <ChatView sendChat={sendChat} />;
      case 'settings':
        return <SettingsView />;
      default:
        return <SenderView sendCall={sendCall} />;
    }
  };

  return (
    <div style={{
      position: 'relative',
      width: '100%',
      height: '100vh',
      display: 'flex',
      flexDirection: 'column',
      backgroundColor: '#000', // Solid black background
      color: 'white',
      paddingTop: 'env(safe-area-inset-top)',
      paddingBottom: '120px' // Space for floating bottom nav
    }}>
      <div style={{ padding: '0 20px', paddingTop: '10px' }}>
        <PermissionGuide />
      </div>

      <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        {renderContent()}
      </div>

      {/* Floating Bottom Navigation */}
      <div style={{
        position: 'fixed',
        bottom: '40px',
        left: '50%',
        transform: 'translateX(-50%)',
        width: 'calc(100% - 48px)',
        maxWidth: '400px',
        zIndex: 1000
      }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-around',
          alignItems: 'center',
          backgroundColor: 'rgba(15, 23, 42, 0.9)',
          backdropFilter: 'blur(10px)',
          border: '1px solid rgba(51, 65, 85, 0.5)',
          padding: '8px',
          borderRadius: '24px',
          boxShadow: '0 20px 50px rgba(0,0,0,0.6)'
        }}>
          <button
            onClick={() => setActiveTab('phone')}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '56px',
              height: '56px',
              backgroundColor: activeTab === 'phone' ? 'rgba(30, 41, 59, 0.7)' : 'transparent',
              borderRadius: '16px',
              border: 'none',
              color: activeTab === 'phone' ? '#3b82f6' : '#64748b',
              transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)'
            }}
          >
            <Phone size={28} strokeWidth={activeTab === 'phone' ? 2.5 : 2} />
          </button>
          <button
            onClick={() => setActiveTab('chat')}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '56px',
              height: '56px',
              backgroundColor: activeTab === 'chat' ? 'rgba(30, 41, 59, 0.7)' : 'transparent',
              borderRadius: '16px',
              border: 'none',
              color: activeTab === 'chat' ? '#3b82f6' : '#64748b',
              transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)'
            }}
          >
            <MessageSquare size={28} strokeWidth={activeTab === 'chat' ? 2.5 : 2} />
          </button>
          <button
            onClick={() => setActiveTab('settings')}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '56px',
              height: '56px',
              backgroundColor: activeTab === 'settings' ? 'rgba(30, 41, 59, 0.7)' : 'transparent',
              borderRadius: '16px',
              border: 'none',
              color: activeTab === 'settings' ? '#3b82f6' : '#64748b',
              transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)'
            }}
          >
            <Settings size={28} strokeWidth={activeTab === 'settings' ? 2.5 : 2} />
          </button>
        </div>
      </div>
    </div>
  );
};

function App() {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
}

export default App;
