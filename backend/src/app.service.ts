import {
  ConflictException,
  Injectable,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import { Client, Pool } from 'pg';

type DatabaseConfig = {
  host: string;
  port: number;
  user: string;
  password: string;
  database: string;
};

type StoredMessage = {
  clientMessageId: string;
  messageId: string;
  status: string;
  text: string;
};

@Injectable()
export class AppService implements OnModuleInit, OnModuleDestroy {
  private pool!: Pool;

  async onModuleInit() {
    const config = this.getDatabaseConfig();
    await this.createDatabaseIfNeeded(config);

    this.pool = new Pool(config);
    await this.pool.query('CREATE EXTENSION IF NOT EXISTS pgcrypto');
    await this.pool.query(`
      CREATE TABLE IF NOT EXISTS messages (
        message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        client_message_id VARCHAR(100) UNIQUE NOT NULL,
        text TEXT NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'SENT',
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
      )
    `);
  }

  async onModuleDestroy() {
    await this.pool?.end();
  }

  getHello(): string {
    return 'Hello World!';
  }

  getHealth() {
    return {
      status: 'ok',
      service: 'chatfat-backend',
    };
  }

  async createMessage(clientMessageId: string, text: string) {
    const insertResult = await this.pool.query<StoredMessage>(
      `
        INSERT INTO messages (client_message_id, text, status)
        VALUES ($1, $2, 'SENT')
        ON CONFLICT (client_message_id) DO NOTHING
        RETURNING
          client_message_id AS "clientMessageId",
          message_id AS "messageId",
          status,
          text
      `,
      [clientMessageId, text],
    );
    const insertedMessage = insertResult.rows[0];
    if (insertedMessage) {
      return this.toMessageResponse(insertedMessage);
    }

    const existingResult = await this.pool.query<StoredMessage>(
      `
        SELECT
          client_message_id AS "clientMessageId",
          message_id AS "messageId",
          status,
          text
        FROM messages
        WHERE client_message_id = $1
      `,
      [clientMessageId],
    );
    const existingMessage = existingResult.rows[0];

    if (!existingMessage) {
      throw new Error('Message was not found after insert conflict');
    }
    if (existingMessage.text !== text) {
      throw new ConflictException(
        'clientMessageId already exists with different text',
      );
    }

    return this.toMessageResponse(existingMessage);
  }

  private getDatabaseConfig(): DatabaseConfig {
    const password = process.env.DB_PASSWORD;
    if (!password) {
      throw new Error('DB_PASSWORD is required');
    }

    return {
      host: process.env.DB_HOST ?? '127.0.0.1',
      port: Number(process.env.DB_PORT ?? 5432),
      user: process.env.DB_USER ?? 'postgres',
      password,
      database: process.env.DB_NAME ?? 'chatfat',
    };
  }

  private async createDatabaseIfNeeded(config: DatabaseConfig) {
    const adminClient = new Client({ ...config, database: 'postgres' });
    await adminClient.connect();

    try {
      const result = await adminClient.query(
        'SELECT 1 FROM pg_database WHERE datname = $1',
        [config.database],
      );
      if (result.rowCount === 0) {
        const databaseName = config.database.replaceAll('"', '""');
        await adminClient.query(`CREATE DATABASE "${databaseName}"`);
      }
    } finally {
      await adminClient.end();
    }
  }

  private toMessageResponse(message: StoredMessage) {
    return {
      clientMessageId: message.clientMessageId,
      messageId: message.messageId,
      status: message.status,
    };
  }
}
