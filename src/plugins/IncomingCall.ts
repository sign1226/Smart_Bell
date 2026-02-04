import { registerPlugin } from '@capacitor/core';

export interface IncomingCallPlugin {
    show(options: { name: string }): Promise<{ success: boolean }>;
    dismiss(): Promise<{ success: boolean }>;
    getRingtones(options?: { type?: 'ringtone' | 'notification' }): Promise<{ ringtones: { title: string; uri: string }[] }>;
    saveRingtoneSettings(options: {
        uri: string,
        host?: string,
        vibrationEnabled?: boolean,
        vibrationPattern?: string
    }): Promise<{ success: boolean }>;
    saveChatSettings(options: { uri: string }): Promise<void>;
    startRingtone(): Promise<{ success: boolean }>;
    stopRingtone(): Promise<{ success: boolean }>;
    checkPermissions(): Promise<{ overlay: boolean, batteryOptimization: boolean, notifications: boolean }>;
    requestOverlayPermission(): Promise<void>;
    requestIgnoreBatteryOptimization(): Promise<void>;
    requestNotificationPermission(): Promise<void>;
    syncContacts(options: { contacts: string }): Promise<void>;
    startService(options: {
        host: string;
        port: number;
        topic: string;
        clientId: string;
        deviceId: string
    }): Promise<void>;
    stopService(): Promise<void>;
    getPendingChatMessages(): Promise<{ messages: any[] }>;
    getPendingWidgetCall(): Promise<{ targetId: string | null; targetName: string | null }>;
    clearPendingWidgetCall(): Promise<void>;
}

const IncomingCall = registerPlugin<IncomingCallPlugin>('IncomingCall');

export default IncomingCall;
