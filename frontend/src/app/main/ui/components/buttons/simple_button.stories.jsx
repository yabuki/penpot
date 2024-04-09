import * as React from "react";

import Components from "@target/components";
import Icons from "@target/icons";

export default {
  title: "Components/Simple Button",
  component: Components.SimpleButton,
  argTypes: {
    variant: {
      description: "type description",
      options: ["primary", "secondary"],
      control: { type: "radio" },
    },
    children: {
      description: "Call to action",
      control: {
        type: "text",
      },
    },
  },
};

export const Default = {
  args: {
    children: "SimpleButton",
  },
  render: (args) => (
    <Components.StoryWrapper>
      <Components.SimpleButton>{args.children}</Components.SimpleButton>
    </Components.StoryWrapper>
  ),
};
