import { IntersectionType, OmitType, PartialType, PickType } from '@nestjs/swagger';

import { Expose } from 'class-transformer';


export class EventDto{
  readonly _id: string;
  readonly attendents: Map<string,boolean>;
  readonly creator_id: string;
  readonly date: Date;
  readonly description: string;
  readonly isPrivate: boolean;
  readonly location: string;
  readonly name: string;
  num_ratings: number;
  rating: number;
}


export class CreateEventDto extends OmitType(EventDto,['_id','attendents','rating','num_ratings']){}

export class SearchEventDto extends PickType(EventDto,['_id','name','creator_id','date','location']){
  date: Date;
}



export class EditEventDto extends OmitType(EventDto, ['_id','creator_id']){}
export class RateEventDto extends PickType(EventDto,['_id','rating']){}