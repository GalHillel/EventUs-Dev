
import { HttpException, HttpStatus, Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, isValidObjectId } from 'mongoose';
import { UserEvent } from './event.model';
import { CreateEventDto, EditEventDto, RateEventDto, SearchEventDto } from '../dto/event.dto';
import { ObjectId } from 'mongoose';

import { User } from '../user/user.model';
//import { string } from '../dto/id.dto';

@Injectable()
export class EventService {
  constructor(
    @InjectModel(UserEvent.name) private readonly userEventModel: Model<UserEvent>,
    
  ) {}

  /**
   * Adds a user id to the events attendents list
   * @param _id event _id
   * @param userId user _id
   * @returns updated event
   */
  async addUser(_id:string,userId:string): Promise<UserEvent>{
    const userEvent = await this.getUserEvent(_id);
    //dupe check
    if (userEvent.attendents.has(userId)){
      throw new HttpException("User exists in event!",HttpStatus.CONFLICT);
    } 
    userEvent.attendents.set(userId,false);
    await userEvent.save();
    return userEvent;
  }

  async createEvent(createEventDto: CreateEventDto): Promise<UserEvent> {
    
    const createdEvent = new this.userEventModel(createEventDto);
    createdEvent.attendents.set(createdEvent.creator_id,true)
    return createdEvent.save();
  }

  /**
   * Deletes event from database
   * @param _id event id
   */
  async deleteEvent(_id:string): Promise<void>{
    await this.userEventModel.findById(_id).deleteOne().exec();
  }

  async editEvent(_id:string,edit: EditEventDto): Promise<void>{
    
    const up = await this.userEventModel.updateOne({_id:_id},edit).exec();
  
  }

  async findAllEvents(): Promise<UserEvent[]> {
    return this.userEventModel.find().exec();
  }

  async getCreator_id(_id: string): Promise<string> {
    return (await this.getUserEvent(_id,'creator_id')).creator_id; 
  }

  /**
   * Get a single event by event Id
   * @param _id _id field of the desired event
   * @param field get only selected fields from event
   * @returns Desired event
   */
  async getUserEvent(_id:string,field?:string): Promise<UserEvent>{
    return this.userEventModel.findById(_id,field).exec().then((userEvent) => { 
      if (!userEvent) throw new NotFoundException('Event '+_id+' not Found');
      return userEvent;
    });
  }

  // TODO: error handling
  /**
   * Get all event by ids
   * @param _ids List of _id field of desired event
   * @returns List of desired event
   */
  async getUserEvents(_ids:string[],fields?:string): Promise<UserEvent[]>{
    return this.userEventModel.find({ _id: { $in: _ids } },fields).exec();
  }
  
  async getUserIds(_id: string): Promise<string[]> {
    return Array.from((await this.getUserEvent(_id,'attendents')).attendents.keys()); 
  }

  async acceptUser(_id:string,userId:string): Promise<void>{
    var k = `attendents.${userId}`
    const up = await this.userEventModel.updateOne({_id:{$in:[_id]}},{$set:{[k]:true}}).exec();
  }

  /**
   * finds all events matching the search terms
   * @param searchTerms search fields
   * @returns List of userEvents
   */
  async search(searchTerms: SearchEventDto,fields?:string): Promise<UserEvent[]>{
    
    return this.userEventModel.find(searchTerms,fields).exec();
  }

  async search_event(searchTerms: SearchEventDto,fields?:string): Promise<UserEvent[]>{
    var search_query = [];
    for (const key in searchTerms){
      if (searchTerms[key] != undefined && (isValidObjectId(searchTerms[key]) || (key != "_id" && key != "creator_id")) && (key != "date" || !isNaN(Date.parse(searchTerms[key].toString())))){
        search_query.push({[key]:searchTerms[key]});
      }
    }
    return this.userEventModel.find(
      {
        $and:[
          {
            date:{$gte:new Date()}},
          {
            $or:search_query
          }
        ]
      },fields).exec();
    

  }

  async rateEvent(eventId: string, rateDTO: RateEventDto): Promise<EditEventDto> {
    return this.userEventModel.findById(eventId, 'rating num_ratings').exec().then((event) => {
      if (!event) {
        throw new NotFoundException('Event not found');
      }
      // Need to check if the user already rate this event
      // Update the event with the new rating
      const newRating = (event.rating * event.num_ratings + rateDTO.rating) / (event.num_ratings + 1);
      const dto = {rating: newRating, num_ratings: event.num_ratings + 1} as EditEventDto;
      return dto;
    });
  } 


  /**
   * Removes a user from the events attendents list
   * @param _id event _id
   * @param userId user _id
   */
  async removeUser(_id:string,userId:string): Promise<UserEvent>{
    
    //const tmp = await this.userEventModel.findById(_id).updateMany({},{ $unset:["attendents."+userId]}).exec();
    
    const userEvent = await this.userEventModel.findById(_id).exec();
    userEvent.attendents.delete(userId);
    return userEvent.save()
  }

  // Implement other CRUD operations as needed
}
