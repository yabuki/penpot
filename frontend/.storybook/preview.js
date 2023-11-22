// import "../resources/public/css/main.css";

//import "../resources/public/images/sprites/symbol/icons.svg";
//import "../resources/public/images/sprites/symbol/cursors.svg";

/** @type { import('@storybook/react').Preview } */
const preview = {
  parameters: {
    actions: { argTypesRegex: "^on[A-Z].*" },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
};

export default preview;
