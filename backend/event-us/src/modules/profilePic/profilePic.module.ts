import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { ProfilePicController } from './profilePic.controller';
import { ProfilePicService } from './profilePic.service';
import { ProfilePic, ProfilePicSchema } from './profilePic.model';

@Module({
    imports: [MongooseModule.forFeature([{ name: ProfilePic.name, schema: ProfilePicSchema }])],
    controllers: [ProfilePicController],
    providers: [ProfilePicService],
  })
export class ProfilePicModule {}

