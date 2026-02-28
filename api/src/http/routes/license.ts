import { Elysia } from "elysia";
import { MongoServerError } from "mongodb";
import { z } from "zod";

import { db } from "../../database/client.js";
import { generate as generateLicense, view as viewLicense } from "../plugins/code.js";

type LicenseTier = "default";
type LicenseValue = {
  ownerId: string;
  tier: LicenseTier;
};
type LicenseView = {
  valid: boolean;
  type?: string;
  value?: unknown;
};
type LicenseDoc = {
  id: string;
  type?: string;
  value?: unknown;
};

const DEFAULT_OWNER_ID = "default";
const DEFAULT_TIER: LicenseTier = "default";
const DEFAULT_CODE_PREFIX = "pluginsx_";

const licenseCollection = db.collection<LicenseDoc>("license");
const licenseOwnerIndexReady = licenseCollection.createIndex(
  { type: 1, "value.ownerId": 1 },
  {
    unique: true,
    name: "pluginsx_license_owner_unique_idx",
    partialFilterExpression: {
      type: "pluginsx_license",
      "value.ownerId": { $exists: true },
    },
  },
);

const defaultLicenseSchema = z.object({
  code: z.string(),
  tier: z.literal("default"),
  valid: z.boolean(),
});

const generatedDefaultLicenseSchema = defaultLicenseSchema.extend({
  created: z.boolean(),
});

const hasExpectedCodePrefix = (code: string) =>
  code.startsWith(DEFAULT_CODE_PREFIX);

const resolveTierFromValue = (value: unknown): LicenseTier | undefined => {
  if (!value || typeof value !== "object") return undefined;

  const rawTier = (value as Partial<LicenseValue>).tier;
  return rawTier === "default" ? rawTier : undefined;
};

const isDuplicateKeyError = (error: unknown): boolean => {
  if (error instanceof MongoServerError) return error.code === 11000;

  if (!error || typeof error !== "object") return false;

  const rawCode = (error as { code?: unknown }).code;
  if (rawCode === 11000) return true;

  const rawMessage = (error as { message?: unknown }).message;
  return typeof rawMessage === "string" && rawMessage.includes("E11000");
};

const findDefaultLicenseGift = async () =>
  licenseCollection.findOne({
    type: "pluginsx_license",
    "value.ownerId": DEFAULT_OWNER_ID,
  });

const syncGiftDefaultTier = async (
  giftId: string,
  currentTier: LicenseTier | undefined,
) => {
  if (currentTier === DEFAULT_TIER) return;

  await licenseCollection.updateOne(
    { id: giftId, type: "pluginsx_license" },
    {
      $set: {
        "value.ownerId": DEFAULT_OWNER_ID,
        "value.tier": DEFAULT_TIER,
      },
    },
  );
};

const mapLicenseResponse = (code: string, view: LicenseView) => ({
  code,
  tier: resolveTierFromValue(view.value) ?? DEFAULT_TIER,
  valid: view.valid,
});

const regenerateDefaultLicense = async (previousCode?: string) => {
  if (previousCode) {
    await licenseCollection.deleteOne({ id: previousCode, type: "pluginsx_license" });
  }

  try {
    const code = await generateLicense(DEFAULT_OWNER_ID, DEFAULT_TIER);
    const view = await viewLicense(code);
    return {
      code,
      view,
      created: true,
    };
  } catch (error) {
    if (!isDuplicateKeyError(error)) throw error;

    const duplicated = await findDefaultLicenseGift();
    if (!duplicated?.id) throw error;

    const duplicatedTier = resolveTierFromValue(duplicated.value);
    await syncGiftDefaultTier(duplicated.id, duplicatedTier);

    const view = await viewLicense(duplicated.id);
    return {
      code: duplicated.id,
      view,
      created: false,
    };
  }
};

export const licenseRoutes = new Elysia({ prefix: "/license" })
  .post(
    "/generate",
    async () => {
      await licenseOwnerIndexReady;

      const existing = await findDefaultLicenseGift();

      if (existing?.id) {
        if (!hasExpectedCodePrefix(existing.id)) {
          const regenerated = await regenerateDefaultLicense(existing.id);
          return {
            ...mapLicenseResponse(regenerated.code, regenerated.view),
            created: regenerated.created,
          };
        }

        const currentTier = resolveTierFromValue(existing.value);
        await syncGiftDefaultTier(existing.id, currentTier);

        const existingView = await viewLicense(existing.id);
        return {
          ...mapLicenseResponse(existing.id, existingView),
          created: false,
        };
      }

      const generated = await regenerateDefaultLicense();
      return {
        ...mapLicenseResponse(generated.code, generated.view),
        created: generated.created,
      };
    },
    {
      detail: {
        summary: "Generate default license",
        tags: ["License"],
      },
      response: {
        200: generatedDefaultLicenseSchema,
      },
    },
  )
  .get(
    "/view",
    async ({ status }) => {
      await licenseOwnerIndexReady;

      const license = await findDefaultLicenseGift();

      if (!license?.id) {
        return status(404, { message: "default license not found" });
      }

      const currentTier = resolveTierFromValue(license.value);

      if (!hasExpectedCodePrefix(license.id)) {
        const regenerated = await regenerateDefaultLicense(license.id);
        return mapLicenseResponse(regenerated.code, regenerated.view);
      }

      await syncGiftDefaultTier(license.id, currentTier);

      const licenseView = await viewLicense(license.id);
      return mapLicenseResponse(license.id, licenseView);
    },
    {
      detail: {
        summary: "View current default license",
        tags: ["License"],
      },
      response: {
        200: defaultLicenseSchema,
        404: z.object({ message: z.string() }),
      },
    },
  )
  .get(
    "/verify",
    async ({ query, status }) => {
      const code = typeof query.code === "string" ? query.code.trim() : "";

      if (!code) {
        return status(400, { message: "license code is required" });
      }

      const licenseView = await viewLicense(code);
      return licenseView.valid;
    },
    {
      query: z.object({
        code: z.string().min(1),
      }),
      detail: {
        summary: "Verify license by code",
        tags: ["License"],
      },
      response: {
        200: z.boolean(),
        400: z.object({ message: z.string() }),
        404: z.object({ message: z.string() }),
      },
    },
  );
