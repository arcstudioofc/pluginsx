import z from "zod";
import { openapi } from "@elysiajs/openapi";
import { cors } from "@elysiajs/cors";
import { Elysia } from "elysia";
import { node } from "@elysiajs/node";

import { env } from "./config/env.js";
import { logger } from "./utils/logger.js";
import { indexRoutes } from "./http/routes/index.js";
import { licenseRoutes } from "./http/routes/license.js";
import { aboutRoutes } from "./http/routes/about.js";
import { Package } from "./config/package.js";
import { connectDatabase } from "./database/connect.js";

await connectDatabase();

const app = new Elysia({
  name: "PluginsX, API.",
  prefix: "/v2",
  adapter: node()
})
  .use(
    cors({
      origin: [ env.PLUGINSX_URL, env.PLUGINSX_DEV_URL],
      methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
      allowedHeaders: ["Content-Type"],
    }),
  )
  .use(
    openapi({
      mapJsonSchema: {
        zod: z.toJSONSchema,
      },
      documentation: {
        info: {
          title: "ARC Studio, PluginsX.",
          version: Package.version,
          description: "Principal API for PluginsX, Inc.",
        },
        tags: [
          { name: "Default", description: "Default routes" },
          { name: "License", description: "License related routes" },
        ],
      },
    }),
  )
  .use(indexRoutes)
  .use(licenseRoutes)
  .use(aboutRoutes)
  .listen({ port: env.DEFAULT_PORT }, (info) => {
    logger(`🔥 api rodando em ${info.hostname}:${info.port}`);
  });

export type App = typeof app;
