import {
  Controller,
  Get,
  InternalServerErrorException,
  LoggerService,
  ServiceUnavailableException,
} from '@nestjs/common';
import { InjectConnection } from '@nestjs/mongoose';
import { NextFunction, Request, Response } from 'express';
import { Connection } from 'mongoose';

const SERVICE = process.env.SERVICE_NAME || 'eventus-api';
const VERSION = process.env.APP_VERSION || 'dev';
const HEALTH_PATHS = ['/health/live', '/health/ready'];
const DEFAULT_ERROR_RATE = 0;

export function log(level: string, msg: string, extra: Record<string, unknown> = {}) {
  const line = { time: new Date().toISOString(), level, service: SERVICE, version: VERSION, msg, ...extra };
  process.stdout.write(`${JSON.stringify(line)}\n`);
}

export class JsonLogger implements LoggerService {
  log(m: unknown, context?: string) {
    log('info', String(m), { context });
  }
  error(m: unknown, stack?: string, context?: string) {
    log('error', String(m), { context, stack });
  }
  warn(m: unknown, context?: string) {
    log('warn', String(m), { context });
  }
  debug(m: unknown, context?: string) {
    log('debug', String(m), { context });
  }
  verbose(m: unknown, context?: string) {
    log('debug', String(m), { context });
  }
}

export function requestLog(req: Request, res: Response, next: NextFunction) {
  const start = process.hrtime.bigint();
  res.on('finish', () => {
    const path = req.originalUrl.split('?')[0];
    const code = res.statusCode;
    const level = code >= 500 ? 'error' : code >= 400 ? 'warn' : 'info';
    log(level, `${req.method} ${path} ${code}`, {
      method: req.method,
      path,
      statusCode: code,
      durationMs: Math.round(Number(process.hrtime.bigint() - start) / 1e4) / 100,
      ip: req.ip,
    });
  });
  next();
}

export function chaos(req: Request, res: Response, next: NextFunction) {
  const rate = Number.parseFloat(process.env.CHAOS_ERROR_RATE ?? String(DEFAULT_ERROR_RATE));
  const path = req.originalUrl.split('?')[0];
  if (!rate || HEALTH_PATHS.includes(path) || Math.random() >= rate) {
    next();
    return;
  }
  res.status(500).json({ statusCode: 500, message: 'Internal Server Error', error: 'chaos' });
}

@Controller()
export class PlatformController {
  constructor(@InjectConnection() private readonly connection: Connection) {}

  @Get('health/live')
  live() {
    return { status: 'ok', uptime: Math.round(process.uptime()) };
  }

  @Get('health/ready')
  ready() {
    const state = this.connection.readyState;
    if (state !== 1) {
      throw new ServiceUnavailableException({ status: 'not-ready', mongo: state });
    }
    return { status: 'ok', mongo: 'connected', version: VERSION };
  }

  @Get('chaos/status')
  status() {
    return {
      errorRate: Number.parseFloat(process.env.CHAOS_ERROR_RATE || '0'),
      version: VERSION,
      pod: process.env.POD_NAME || 'local',
    };
  }

  @Get('chaos/boom')
  boom(): never {
    throw new InternalServerErrorException('deliberate failure');
  }
}
