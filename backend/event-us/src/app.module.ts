import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';

import { UserModule } from './modules/user/user.module';
import { EventModule } from './modules/event/event.module';
import { MessageModule } from './modules/message/message.module';
import { ProfilePicModule } from './modules/profilePic/profilePic.module';
import { PlatformController, chaos, requestLog } from './common/platform';

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/EventUs';

@Module({
  imports: [
    MongooseModule.forRoot(MONGODB_URI, {
      serverSelectionTimeoutMS: 5000,
      retryAttempts: 20,
      retryDelay: 3000,
    }),
    UserModule,
    EventModule,
    MessageModule,
    ProfilePicModule,
  ],
  controllers: [PlatformController],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    consumer.apply(requestLog, chaos).forRoutes('*');
  }
}
