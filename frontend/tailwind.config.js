/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./App.tsx", "./src/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        // Dwara design tokens — the ONLY colors allowed in components.
        canvas: "#F8F9FA",
        primary: "#111827",
        muted: "#6B7280",
        brand: {
          DEFAULT: "#00B177",
          tint: "#D1FAE5",
        },
      },
      borderRadius: {
        card: 24,
        btn: 12,
      },
    },
  },
  plugins: [],
};
