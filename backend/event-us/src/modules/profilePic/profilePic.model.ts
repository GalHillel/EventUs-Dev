import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document } from 'mongoose';
//import { string } from '../dto/id.dto';

@Schema()
export class ProfilePic extends Document {    
    
    @Prop({ required: true })
    icon: Buffer;
    

}

export const ProfilePicSchema = SchemaFactory.createForClass(ProfilePic);