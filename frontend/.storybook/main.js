const path = require("path");

/** @type { import('@storybook/react-webpack5').StorybookConfig } */
const config = {
  stories: ["../stories/*.story.jsx"],
  staticDirs: ['../resources/public'],
  addons: [
    "@storybook/addon-links",
    "@storybook/addon-essentials",
    "@storybook/addon-onboarding",
    "@storybook/addon-interactions",
  ],
  framework: {
    name: "@storybook/react-webpack5",
    options: {},
  },
  docs: {
    autodocs: "tag",
  },

  // async webpackFinal(config, { configType }) {
  //   config.module.rules.push({
	// 		test: /\.css$/,
	// 		use: ['style-loader', 'css-loader'],
	// 		include: path.resolve(__dirname, '../resources/public'),
	// 	});
  //   
  //   config.resolve.roots = [
	// 		path.resolve(__dirname, '../resources/public')
	// 	];
  //   return config;
  // },
};
export default config;
