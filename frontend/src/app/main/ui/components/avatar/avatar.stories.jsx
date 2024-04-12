import * as React from "react";

import Components from "@target/components";
// export default {
//   title: "Components/Avatar",
// };

export default {
  title: "Components/Avatar",
  argTypes: {
    url: {
      description: "Avatar URL",
      control: "text",
    },
    shape: {
      description: "Avatar shape",
      control: "select",
      options: ["rounded", "square"],
    },
    size: {
      description: "Avatar size",
      control: "select",
      options: ["XL", "M", "XS"],
    },
    name: {
      description: "Avatar name",
      control: "text",
    },
    color: {
      description: "Avatar default color",
      control: "range",
      min: 1,
      max: 9,
    },
  },
};

export const Default = {
  args: {
    url: "http://penpot.app",
    shape: "rounded",
    size: "M",
    name: "Helena Kristaw",
    color: 1,
  },
  render: () => (
    <Components.StoryWrapper>
      <div>Avatar</div>
    </Components.StoryWrapper>
  ),
};
