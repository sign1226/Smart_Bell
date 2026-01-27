import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.smartbell.pro',
  appName: 'Smart Bell',
  webDir: 'dist',
  server: {
    androidScheme: 'http'
  }
};

export default config;
