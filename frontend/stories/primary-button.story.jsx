import * as React from 'react';

import pb from "../target/storybook/primary-button-story";

export const Story1 = () => {
  const PrimaryButton = pb.component;
  return <PrimaryButton/>;
};

export default {
  title: 'Component 1',
  component: Story1,
};

