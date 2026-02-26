import { z } from 'zod';

const toOrigin = (value: string) => new URL(value).origin;

const envSchema = z.object({
  DEFAULT_PORT: z.coerce.number("Invalid port").default(3333),

  PLUGINSX_DEV_URL: z.url().transform(toOrigin),
  PLUGINSX_URL: z.url().transform(toOrigin),

  MONGODB_URI: z.string().startsWith("mongodb+srv://"),
  MONGODB_DB_NAME: z.string(),
});

export const env = envSchema.parse(process.env);
