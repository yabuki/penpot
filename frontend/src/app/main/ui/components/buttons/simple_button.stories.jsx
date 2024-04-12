import * as React from "react";

import Components from "@target/components";

export default {
  title: "Components/Simple Button",
  component: Components.SimpleButton,
  argTypes: {
    variant: {
      description: "type description",
      control: "select",
      options: ["primary", "secondary"],
    },
    children: {
      description: "Call to action",
      control: "text",
    },
  },
};

export const Default = {
  args: {
    children: "call to action",
    variant: "primary",
  },
  render: (args) => (
    <Components.StoryWrapper>
      <Components.SimpleButton>{args.children}</Components.SimpleButton>
    </Components.StoryWrapper>
  ),
};
