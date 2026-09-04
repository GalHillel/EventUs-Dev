import { Controller, Post, Body, Get, Param, Patch, Delete, Query, HttpCode, UsePipes, ValidationPipe, HttpStatus } from '@nestjs/common';
import { EventService } from './event.service';
import { CreateEventDto, EditEventDto, SearchEventDto } from '../dto/event.dto';
import { UserEvent, userEventDisplayFields } from './event.model';

import { User, userDisplayFields } from '../user/user.model';
import { UserService } from '../user/user.service';
import { Schema as mongooseSchema } from "mongoose";
import { UserDto } from '../dto/user.dto';


@Controller('events')
export class EventController {
  constructor(private readonly eventService: EventService,private readonly userService: UserService) {}

  @Post("create")
  async createEvent(@Body() createEventDto: CreateEventDto): Promise<UserEvent> {
    const userEvent = await this.eventService.createEvent(createEventDto);
    this.userService.addEvent(createEventDto.creator_id,userEvent.id)
    return userEvent
  }

  @HttpCode(HttpStatus.NO_CONTENT)
  @Delete(':id')
  async delEvent(@Param('id') idStr:string){
    const userIds = await this.eventService.getUserIds(idStr)
    this.eventService.deleteEvent(idStr);
    userIds.forEach((userId) => this.userService.removeEvent(userId,idStr))
  }

  /**
   * events/<event id>/edit, patch request should contain a json with the new fields to be updated
   * @param _id 
   * @param edit 
   */
  @HttpCode(HttpStatus.NO_CONTENT)
  @Patch(':id/edit')
  async editEvent(@Param('id') _id: string, @Body() edit:EditEventDto): Promise<void>{
    this.eventService.editEvent(_id,edit);
  }

  /**
   * events, get a list of events matching the search terms
   * @param searchTerms 
   * @returns list of events with full details
   */
  @Get()
  @UsePipes(new ValidationPipe({ transform: true }))
  async getEvent(@Query() searchTerms: SearchEventDto): Promise<UserEvent[]>{
    return this.eventService.search(searchTerms);
  }

  @Get(":id/creator")
  async getEventCreator_id(@Param("id") _id: string): Promise<User>{
    return  this.eventService.getCreator_id(_id).then((creator) => this.userService.getUser(creator,userDisplayFields))
  }

  /**TODO errpr handling
   * events/<event id>/info, get full event details by id
   * @param _id 
   * @returns full details of one event
   */
  @Get(":id/info")
  async getEventInfo(@Param('id') _id: string): Promise<UserEvent> {
    return this.eventService.getUserEvent(_id);
  }

  /**
   * events/<event id>/users, returns a list of users
   * @param _id event id
   * @returns list of users with only display user fields
   */
  @Get(":id/users")
  async getEventUsers(@Param("id") _id: string): Promise<User[]>{
    return  this.eventService.getUserIds(_id).then((ids) => {
      
      return this.userService.getUsers(ids,userDisplayFields)
    })
  }
  /**
 * events/<event id>/users, returns a list of users
 * @param _id event id
 * @returns list of users with only display user fields
 */
  @Get(":id/profilepics")
  async getEventUserProfilePics(@Param("id") _id: string){
     return  this.eventService.getUserIds(_id).then((ids) => {
       return this.userService.getUserListProfilePics(ids,"profile_pic")
     })
  }

  /**TODO error handling
   * events/<event id>/joinEvent, patch request should contain a json in the form {_id:<user id>}
   * @param idStr event id
   * @param userId user id
   */
  @HttpCode(HttpStatus.NO_CONTENT)
  @Patch(':id/joinEvent')
  async joinEvent(@Param('id') _id: string, @Body('_id') userId: string): Promise<void>{
    
    this.userService.addEvent(userId, _id);
    this.eventService.addUser(_id,userId);
  }

  /**TODO error handling
   * events/<event id>/acceptuser, patch request should contain a json in the form {_id:<user id>}
   * @param idStr event id
   * @param userId user id
   */
  @HttpCode(HttpStatus.NO_CONTENT)
  @Patch(':id/acceptuser')
  async acceptUser(@Param('id') _id: string, @Body('_id') userId: string): Promise<void>{
    this.eventService.acceptUser(_id,userId);
  }

   


  /**
   * events/search, get a list of only the display fields of events matching the search terms
   * @param searchTerms 
   * @returns list of events with partial details
   */
  @Get("search")
  @UsePipes(new ValidationPipe({ transform: true }))
  async searchEvent(@Query() searchTerms: SearchEventDto): Promise<UserEvent[]>{
    return this.eventService.search_event(searchTerms,userEventDisplayFields);
  }

  // Implement other CRUD endpoints as needed
}