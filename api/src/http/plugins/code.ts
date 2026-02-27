// import { randomBytes } from "node:crypto";
import { Code, generateCodeBody } from "@yeytaken/code";
import { mongodbAdapter } from "@yeytaken/code/adapters/mongodb";

import { client, db } from "../../database/client.js";

type TierType = "default" | "plus";

export type LicenseView = {
  valid: boolean;
  type?: string;
  value?: unknown;
};

// const createCodeId = () => randomBytes(12).toString("hex");

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
      body: await generateCodeBody({
        length: 20,
        charset: "abcdefghijklmnopqrstuvwxyz123567890",
      }),
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
