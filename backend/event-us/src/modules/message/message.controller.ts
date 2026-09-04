import { Controller, Post, Body, Get, UsePipes, ValidationPipe, Query, Param } from '@nestjs/common';
import { MessageService } from './message.service';
import { CreateMessageDto, SearchMessageDto } from '../dto/message.dto';
import { Message, messageDisplayFields } from './message.model';
import { UserService } from '../user/user.service';

@Controller('messages')
export class MessageController {
  constructor(
    private readonly messageService: MessageService,
    private readonly userService: UserService
  ) {}

  @Post()
  async createMessage(@Body() createMessageDto: CreateMessageDto): Promise<Message> {
    const message = await this.messageService.createMessage(createMessageDto);
    await this.userService.addMessages(message.receiver_ids,message.id)
    return message
  }

  /**TODO errpr handling
   * messages/<message id>/info, get full message details by id, param _id to set the message as read
   * @param _id 
   * @returns full details of one message
   */
  @Get(":id/info")
  async getMessageInfo(@Param('id') _id: string, @Query('_id') user_id: string): Promise<Message> {
    
    if (user_id != null && _id != null){
      await this.userService.readMessage(user_id,_id);
    }
    return this.messageService.getMessage(_id);
  }

  /**
   * messages, get a list of messages matching the search terms
   * @param searchTerms 
   * @returns list of messages with full details
   */
  @Get()
  @UsePipes(new ValidationPipe({ transform: true }))
  async getMessages(@Query() searchTerms: SearchMessageDto): Promise<Message[]>{
    return this.messageService.search(searchTerms);
  }

  /**
   * messages/search, get a list of only the display fields of messages matching the search terms
   * @param searchTerms 
   * @returns list of messages with partial details
   */
  @Get("search")
  @UsePipes(new ValidationPipe({ transform: true }))
  async searchMessages(@Query() searchTerms: SearchMessageDto): Promise<Message[]>{
    return this.messageService.search(searchTerms,messageDisplayFields);
  }

  // Implement other CRUD endpoints as needed
}