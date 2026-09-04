//import {string} from './id.dto'

import { OmitType } from "@nestjs/swagger";

export class MessageDto{
  readonly _id: string
  readonly content: string;
  readonly date_sent: Date;
  readonly receiver_ids: string[];
  readonly sender_id: string;
  readonly title: string;
}

export class CreateMessageDto extends OmitType(MessageDto,['_id','date_sent']){}
export class SearchMessageDto extends OmitType(MessageDto,['title','content']){}