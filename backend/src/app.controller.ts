import { Body, Controller, Get, Post } from '@nestjs/common';
import { AppService } from './app.service.js';

@Controller()
export class AppController {
  constructor(private readonly appService: AppService) {}

  @Get()
  getHello(): string {
    return this.appService.getHello();
  }

  @Get('health')
  getHealth() {
    return this.appService.getHealth();
  }

  @Post('messages')
  async createMessage(
    @Body() body: { clientMessageId: string; text: string },
  ) {
    return await this.appService.createMessage(body.clientMessageId, body.text);
  }
}
