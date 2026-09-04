import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document, Schema as mongooseSchema } from 'mongoose';
import { LoggedInUserDto } from '../dto/user.dto';
//import { string } from '../dto/id.dto';

@Schema()
export class User extends Document {
    @Prop({default: ""})
    private bio: string;
    @Prop({ required: true })
    name: string;
    @Prop({default: 0})
    private num_ratings: number;
    @Prop({default: 0})
    private rating: number;
    @Prop({ required: true })
    email: string;
    @Prop({ default: [] })
    events: string[];
    @Prop({ default: {} })
    messages: Map<string,boolean>;
    @Prop({ required: true })
    password: string;
    @Prop({ ref: 'profilepics', default: ""})
    profile_pic: string;

    @Prop({ required: true })
    user_type: string;
}

export const userDisplayFields = "_id name user_type profile_pic"
export const userProfileDisplayFields = "_id name user_type profile_pic rating num_ratings events bio"
export const loggedInUserFields =  "_id name user_type profile_pic rating num_ratings events messages bio"
export const UserSchema = SchemaFactory.createForClass(User);