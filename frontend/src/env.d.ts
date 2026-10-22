/**
 * Expo inlines EXPO_PUBLIC_* variables at build time. The project does not
 * depend on @types/node, so declare the minimal shape the app reads.
 */
declare const process: {
  env: {
    EXPO_PUBLIC_API_URL?: string;
    NODE_ENV?: string;
  };
};
