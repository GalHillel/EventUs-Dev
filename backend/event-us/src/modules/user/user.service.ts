import { HttpException, HttpStatus, Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { User, loggedInUserFields, userDisplayFields } from './user.model';
import { UserEvent } from '../event/event.model';
import { ProfilePic } from '../profilePic/profilePic.model';

import { CreateUserDto, EditUserDto, LoginUserDto, SearchUserDto } from '../dto/user.dto';
//import { string } from '../dto/id.dto';
import { Message } from '../message/message.model';
import { CreateMessageDto } from '../dto/message.dto';


@Injectable()
export class UserService {
  
  
  constructor(
    @InjectModel(User.name) private readonly userModel: Model<User>,
    @InjectModel(ProfilePic.name) private readonly profilePicModel: Model<ProfilePic>,
  ) {}

  /**
   * Adds an event id to the users events list
   * @param _id user _id
   * @param eventId event _id
   * @returns updated user
   */
  async addEvent(_id:string,eventId:string): Promise<User>{
    const user = await this.getUser(_id);
    //dupe check
    if (user.events.includes(eventId)){
      throw new HttpException("Event exists for user!",HttpStatus.CONFLICT);
    } 
    user.events.push(eventId);
    await user.save();
    return user;
  }

  /**
   * Adds a message to all users inbox's, there is no check for duplicate messages
   * @param userIds user id's
   * @param msgId msg id
   */
  async addMessages(userIds: string[],msgId:string): Promise<void>{
    //no need to check for duplicate messages as this function gets called only on message creation
    await this.setMessageState(userIds,msgId,false);
  }

  /**
   * Adds a new user to the database if there is no user that shares the same email and is of the same type
   * @param createUserDto DTO containing registration information
   * @returns user object from the database 
   */
  async createUser(createUserDto: CreateUserDto): Promise<User> {
    
    if(await this.userModel.findOne({email:createUserDto.email, user_type:createUserDto.user_type}).exec() != null){
      throw new HttpException(createUserDto.user_type+" with this email already exists!",HttpStatus.CONFLICT)
    }
    const createdUser = new this.userModel(createUserDto);
    return createdUser.save();
  }

  async editUser(_id: string, edit: EditUserDto) {
    
    const user = await this.getUser(_id,"user_type password");
    
    if(edit.password != null){
      if(edit.oldPassword == null){
        throw new HttpException("No old password provided!",HttpStatus.FAILED_DEPENDENCY)
      }
      else{
        if(edit.oldPassword != user.password){
          throw new HttpException("Wrong password!",HttpStatus.FAILED_DEPENDENCY)
        }
      }
    }
    if(edit.email != null){
      if(await this.userModel.findOne({email:edit.email, user_type:user.user_type}).exec() != null){
        throw new HttpException(user.user_type+" with this email already exists!",HttpStatus.CONFLICT)
      }
    }

    const up = await this.userModel.updateOne({_id:_id},edit).exec();
    
    
  }

  async getEventIds(_id: string): Promise<string[]> {
    return (await this.getUser(_id,'events')).events; 
  }

  async getMessageIds(_id: string): Promise<string[]> {
    const obj = await this.getUser(_id,'messages')
    return Array.from(obj.messages.keys());
  }

  async getMessages(_id: string): Promise<User> {
    return (await this.getUser(_id,'messages'));
  }

  async getProfilePicId(_id: string): Promise<string> {
    return (await this.getUser(_id,'profile_pic')).profile_pic;
  }

  /**
   * Get user by user Id
   * @param _id _id field of the desired user
   * @param field get only selected fields from user
   * @returns Desired user
   */
  async getUser(_id:string, field?:string): Promise<User>{
    return this.userModel.findById(_id,field).exec().then((user) => { 
      if (!user) throw new NotFoundException('User '+_id+' not Found');
      return user;
    })
  }

  /**
   * Gets a user with matching email, password, and user_type from the database 
   * @param loginUserDto DTO containing email, password and user_type
   * @returns user object from the database with only the user display fields or null if doesn't exist
   */
  async loginUser(loginUserDto: LoginUserDto): Promise<User>{
    if(Object.keys(loginUserDto).length === 0){
      throw new HttpException("Empty!",HttpStatus.NOT_ACCEPTABLE)
    }
    return this.userModel.findOne(loginUserDto,loggedInUserFields).exec().then((user)=>{
      if(user == null){
        throw new HttpException("Incorrect credentials!",HttpStatus.FORBIDDEN)
      }
      return user;
    })
    
  }


  // TODO: error handling
  /**
   * Get all users by ids
   * @param _ids List of _id field of desired users
   * @param fields get only selected fields from each user
   * @returns List of desired users
   */
  async getUsers(_ids:string[],fields?:string): Promise<User[]>{
    return await this.userModel.find({ _id: { $in: _ids } },fields).exec();
  }

  async getUserListProfilePics(_ids:string[],fields?:string){
    return this.userModel.find({ _id: { $in: _ids },profile_pic:{$ne:""} },fields).populate('profile_pic','icon',this.profilePicModel).exec();
  }


  /**
   * get a list of users matching the search term
   * @param searchTerms 
   * @param fields 
   * @returns 
   */
  async search(searchTerms: SearchUserDto,fields?:string): Promise<User[]>{
    return this.userModel.find(searchTerms,fields).exec();
  }

  /**
   * removes an event id from the users events list
   * @param _id user _id
   * @param eventId event _id
   * @returns updated user
   */
  async removeEvent(_id:string,eventId:string): Promise<void>{
    await this.userModel.updateOne({_id:_id},{ $pull: {events: eventId} }).exec();
  }

  async setMessageState(userIds:string[],msgId:string,read:boolean){
    return this.userModel.updateMany({_id:{$in:userIds}}, {$set:{[`messages.${msgId}`]:read}}).exec()
  }

  /**
   * removes a message from a users inbox's
   * @param userId user id's
   * @param msgId msg id
   */
  async removeMessage(userId: string,msgId:string): Promise<void>{
    //no need to check for duplicate messages as this function gets called only on message creation
    await this.userModel.findById(userId).updateOne({},{ $unset:{[`messages.${msgId}`]:1}}).exec();
    
  }



  /**
   * set a message as read
   * @param userId user's id
   * @param msgId msg id
   */
  async readMessage(userId: string, msgId: string) {
    this.setMessageState([userId],msgId,true);
  }
  // Implement other CRUD operations as needed
}