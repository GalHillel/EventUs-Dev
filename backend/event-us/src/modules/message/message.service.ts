import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Message, messageDisplayFields } from './message.model';
import { CreateMessageDto, SearchMessageDto } from '../dto/message.dto';
import { User } from '../user/user.model';
//import { string } from '../dto/id.dto';

@Injectable()
export class MessageService {
  constructor(@InjectModel(Message.name) private readonly messageModel: Model<Message>,
  @InjectModel(User.name) private readonly userModel: Model<User>) {}

  async createMessage(createMessageDto: CreateMessageDto): Promise<Message> {
    const createdMessage = new this.messageModel(createMessageDto);
    return createdMessage.save();
  }

  

  /**
   * Get message by message Id
   * @param _id _id field of the desired message
   * @param field get only selected fields from message
   * @returns Desired message
   */
  async getMessage(_id:string,field?:string): Promise<Message>{
    return this.messageModel.findById(_id,field).populate('sender_id','name',this.userModel).exec().then((message) => { 
      if (!message) throw new NotFoundException('Message '+_id+' not Found');
      return message;
    })
    
  }

  /**
   * Get message contents by message Id
   * @param _id _id field of the desired message
   * @returns contents of desired message
   */
  async getMessageContent(_id:string): Promise<string>{
    return (await this.getMessage(_id)).content;
  }

  async search(searchTerms: SearchMessageDto,fields?:string): Promise<Message[]>{
    return this.messageModel.find(searchTerms,fields).populate('sender_id','name',this.userModel).exec();
  }

  // TODO: error handling
  /**
   * Get all messages by ids
   * @param _ids List of _id field of desired messages
   * @returns List of desired messages
   */
  async getMessages(_ids:string[],fields?:string): Promise<Message[]>{
    return this.messageModel.find({ _id: { $in: _ids } },fields).populate('sender_id','name',this.userModel).exec();
  }

  /**
   * Get sender Id by message Id
   * @param _id message id
   * @returns sender Id
   */
  async getSenderId(_id:string): Promise<string>{
    return (await this.messageModel.findById(_id,'sender_id')).sender_id
  }

  /**
   * removes the receiver from the message receiver list
   * @param msgId 
   * @param userId 
   */
  async removeReceiver(msgId:string, userId:string){
    this.messageModel.updateOne({_id:msgId},{$pull:{receiver_ids:userId}})
  }
  

  // Implement other CRUD operations as needed
}