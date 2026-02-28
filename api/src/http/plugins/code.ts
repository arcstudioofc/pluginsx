import { Code } from "@yeytaken/code";
import { mongodbAdapter } from "@yeytaken/code/adapters/mongodb";

import { client, db } from "../../database/client.js";

type TierType = "default" | "plus";

export type LicenseView = {
  valid: boolean;
  type?: string;
  value?: unknown;
};

const license = Code({
  database: mongodbAdapter(db, { client }),
  dbName: "license",
});

export async function generate(ownerId: string, tier: TierType) {
  const codePrefix = tier === "plus" ? "premium_" : "pluginsx_";

  return await license.generate({
    type: "pluginsx_license",
    value: {
      product: "pluginsx",
      ownerId,
      tier,
    },
    codeOptions: {
      prefix: `${codePrefix}`,
    },
  });
}

export async function view(pluginsx_license: string): Promise<LicenseView> {
  const licenseView = await license.view(pluginsx_license);
  if (!licenseView) {
    return { valid: false };
  }

  return {
    valid: true,
    type: licenseView.type,
    value: licenseView.value,
  };
}
