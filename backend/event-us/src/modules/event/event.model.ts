import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document } from 'mongoose';
//import { string } from '../dto/id.dto';



@Schema()
export class UserEvent extends Document {
    @Prop({ required: true })
    private date: Date;
    @Prop({ default: "" })
    private description: string;
    @Prop({default: false})
    private isPrivate: boolean;
    @Prop({ default: "TBD" })
    private location: string;
    @Prop({ required: true })
    private name: string;
    @Prop({default: 0})
    num_ratings: number;
    @Prop({default: 0.0})
    rating: number;
    @Prop({ default: {} })
    attendents: Map<string,boolean>;
    @Prop({ required: true })
    creator_id: string; 
}

export const userEventDisplayFields = "_id name date location isPrivate num_ratings rating"

export const UserEventSchema = SchemaFactory.createForClass(UserEvent);