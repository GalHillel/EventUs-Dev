import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

import { AppModule } from './app.module';
import { JsonLogger, log } from './common/platform';

const PORT = Number.parseInt(process.env.PORT || '3000', 10);
const HOST = process.env.HOST || '0.0.0.0';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { logger: new JsonLogger() });

  app.enableCors();
  app.useGlobalPipes(new ValidationPipe({ transform: true }));
  app.enableShutdownHooks();

  const options = new DocumentBuilder().setTitle('EventUs API').setVersion(process.env.APP_VERSION || 'dev').build();
  SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, options));

  await app.listen(PORT, HOST);
  log('info', 'server started', { port: PORT, host: HOST });
}

process.on('unhandledRejection', (reason) => log('error', 'unhandled rejection', { reason: String(reason) }));
process.on('uncaughtException', (err: Error) => {
  log('error', 'uncaught exception', { reason: err.message });
  process.exit(1);
});

bootstrap();
