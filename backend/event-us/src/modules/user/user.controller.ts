import { Controller, Post, Body, Get, Param, ValidationPipe, UseInterceptors,ClassSerializerInterceptor, Query, Put, Patch, ParseUUIDPipe, HttpException, HttpStatus, HttpCode, UsePipes } from '@nestjs/common';
import { UserService } from './user.service';
import { CreateUserDto, EditUserDto, LoginUserDto, SearchUserDto } from '../dto/user.dto';
import { User, loggedInUserFields, userDisplayFields, userProfileDisplayFields } from './user.model';
import { UserEvent, userEventDisplayFields } from '../event/event.model';
import { Message, messageDisplayFields } from '../message/message.model';
import { EventService } from '../event/event.service';
import { ProfilePicService } from '../profilePic/profilePic.service';
import { MessageService } from '../message/message.service';
import { CreateMessageDto } from '../dto/message.dto';
import { EditEventDto, RateEventDto } from '../dto/event.dto';
import { log } from '../../common/platform';


@Controller('users')
export class UserController {
  constructor(
    private readonly userService: UserService,
    private readonly eventService: EventService,
    private readonly profilePicService: ProfilePicService,
    private readonly messageService: MessageService,
  ) {}

  @Post("register")
  async createUser(@Body() createUserDto: CreateUserDto): Promise<User> {
    try{
      return await this.userService.createUser(createUserDto);
    } catch(e){
      if(e instanceof HttpException)
        throw new HttpException(e.message,e.getStatus());
    }

  }

  /** TODO
   * users/<user id>/edit, patch request should contain a json with the new fields to be updated
   * @param _id user id
   * @param editUserDto new data
   */
  @HttpCode(HttpStatus.NO_CONTENT)
  @Patch(':id/edit')
  @UsePipes(new ValidationPipe({ transform: true }))
  async editUser(@Param('id') _id:string, @Body() editUserDto:EditUserDto): Promise<void>{
    
    try{
      await this.userService.editUser(_id,editUserDto);
      
    } catch(e){
      log('error', 'edit user failed', { userId: _id, reason: e.message });
      if(e instanceof HttpException){
        
        throw new HttpException(e.message,e.getStatus());
      }
        
    }

  }


  /**TODO error handling
   * users/<user_id>/rate, patch request should contain a json in the form {_id:<user id>}
   * @param idStr event id
   * @param userId user id
   */
  @HttpCode(HttpStatus.NO_CONTENT)
  @Patch(':id/rate')
  async rateEvent(@Param('id') _id: string, @Body() ratingDTO:RateEventDto): Promise<void>{
    log('info', 'user rating event', { userId: _id, eventId: ratingDTO._id });
    try{
      this.eventService.rateEvent(ratingDTO._id,ratingDTO).then((editEventDto)=> {
        this.exitEvent(_id,ratingDTO._id).then(()=> {
          this.eventService.editEvent(ratingDTO._id,editEventDto);
        });
      });
    }
    catch(e){
      log('error', 'rate event failed', { userId: _id, reason: e.message });
      if(e instanceof HttpException){
        
        throw new HttpException(e.message,e.getStatus());
      }
        
    }

  }

  /** TODO error handling
   * users/<user id>/exitEvent, Patch request should contain a json in the form {_id:<event id>}
   * @param _id user id
   * @param eventId event id
   * 
   */
  @HttpCode(HttpStatus.NO_CONTENT)
  @Patch(':id/exitEvent')
  async exitEvent(@Param('id') _id: string,  @Body('_id') eventId: string): Promise<void>{
    
    if (await this.eventService.removeUser(eventId,_id) != null){
      await this.userService.removeEvent(_id,eventId);
    }
  }

  /**
   * users, get a list of users matching the search terms
   * @param searchTerms 
   * @returns list of users with full details
   */
  @Get()
  @UsePipes(new ValidationPipe({ transform: true }))
  async findAllUsers(@Query() searchTerms: SearchUserDto): Promise<User[]> {
    return this.userService.search(searchTerms);
  }

  @Get(':id/messageField')
  async getMessages(@Param('id') _id: string): Promise<User>{
    return this.userService.getMessages(_id);
  }

  /**TODO error handling
   * users/<user id>/display, get user display details by id
   * @param _id 
   * @returns UserDisplay of user fields
   */
  @Get(":id/display")
  async getUserDisplay(@Param('id') _id: string): Promise<User> {
    return this.userService.getUser(_id,userDisplayFields);
  }

  @Get(':id/events')
  async getUserEvents(@Param('id') _id: string): Promise<UserEvent[]>{
    return this.userService.getEventIds(_id).then((ids) => this.eventService.getUserEvents(ids,userEventDisplayFields));
  }

  @Get(':id/messages')
  async getUserMessages(@Param('id') _id: string): Promise<Message[]>{
    return this.userService.getMessageIds(_id).then((ids) => this.messageService.getMessages(ids));
  }

  /**TODO error handling
   * users/<user id>/profile, get full profile details by id
   * @param _id 
   * @returns profile of user fields
   */
  @Get(":id/profile")
  async getUserProfile(@Param('id') _id: string): Promise<User> {
    return this.userService.getUser(_id,userProfileDisplayFields);
  }

  @Get(':id/profilepic')
  async getUserProfilePicIcon(@Param('id') _id: string): Promise<Buffer>{
    return this.userService.getProfilePicId(_id).then((profile_id) => this.profilePicService.getIcon(profile_id));
  }

  /**
   * users/login
   * @param loginUserDto dict containing {email:<user email>, password:<user password>, user_type:<Organizer or Participant>}
   * @returns user on success (200), http error otherwise
   */
  @Get("login")
  async login(@Query() loginUserDto: LoginUserDto): Promise<User>{
    try{

      return await this.userService.loginUser(loginUserDto);

    } catch(e){
      
      if(e instanceof HttpException)
        throw new HttpException(e.message,e.getStatus());
    }
    
  }

  /** TODO error handling
   * users/<user id>/removeMessage, Patch request should contain a json in the form {_id:<message id>}
   * @param _id user id
   * @param eventId event id
   */
  @HttpCode(HttpStatus.NO_CONTENT)
  @Patch(':id/removeMessage')
  async removeMessage(@Param('id') _id: string,  @Body('_id') msgId: string): Promise<void>{
    
    await this.userService.removeMessage(_id,msgId);
    await this.messageService.removeReceiver(msgId,_id)
    
  }

  /**
   * users/search, get a list of only the display fields of users matching the search terms
   * @param searchTerms 
   * @returns list of users with partial details
   */
  @Get("search")
  @UsePipes(new ValidationPipe({ transform: true }))
  async searchEvent(@Query() searchTerms: SearchUserDto): Promise<User[]>{
    return this.userService.search(searchTerms,userDisplayFields);
  }

  // Implement other CRUD endpoints as needed
}