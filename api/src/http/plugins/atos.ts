import { randomBytes } from "node:crypto";
import { GiftManager } from "atosjs";
import { env } from "../../config/env.js";

type TierType = "default" | "plus";

const createCodeId = () => randomBytes(12).toString("hex");

const license = new GiftManager({
  mongodb: { connect: env.MONGODB_URI, dbName: env.MONGODB_DB_NAME },
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
    edit: { code: `${codePrefix}${createCodeId()}` },
  });
}

export async function view(pluginsx_license: string) {
  return await license.view(pluginsx_license);
}
