module.exports = function (api) {
  api.cache(true);
  return {
    // Nativewind v4 + Reanimated 4 (worklets package provides the babel plugin on SDK 57)
    presets: [["babel-preset-expo", { jsxImportSource: "nativewind" }], "nativewind/babel"],
    plugins: ["react-native-worklets/plugin"],
  };
};
