import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Schema as MongoScheme } from "mongoose";
import { Document } from 'mongoose';
import { Schema as mongooseSchema } from "mongoose";
//import { string } from '../dto/id.dto';



@Schema()
export class Message extends Document {
    @Prop({ default: Date.now })
    date_sent: Date;
    @Prop({ default: "" })
    title: string;
    @Prop({ default: "" })
    content: string;
    @Prop({ required: true })
    receiver_ids: string[];
    @Prop({ type: [{ type: String, ref: 'users' }], required:true})
    sender_id: string;
}
export const messageDisplayFields = "_id sender_id title"
export const MessageSchema = SchemaFactory.createForClass(Message);