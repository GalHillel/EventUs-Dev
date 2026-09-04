import { OmitType, PartialType, PickType } from '@nestjs/swagger';


export class UserDto{
  readonly _id: string;
  readonly bio: string;
  readonly email: string;
  readonly events: string[];
  readonly messages: Map<string,boolean>;
  readonly name: string;
  readonly num_ratings: number;
  readonly password: string;
  readonly profile_pic: string;
  readonly rating: number;
  readonly user_type: string;
}

export class ParticipantDto extends OmitType(UserDto,['rating','num_ratings']){}
export class CreateUserDto extends OmitType(UserDto,['_id','profile_pic','messages','events','rating','num_ratings','bio']){}
export class LoginUserDto extends PickType(UserDto,['email','password','user_type']){}
export class SearchUserDto extends PickType(UserDto,['_id','name','email','user_type']){}
export class EditUserDto extends PickType(UserDto,['name','email','password','bio','profile_pic','rating','num_ratings','messages']){
  readonly oldPassword: string;
}
export class LoggedInUserDto extends OmitType(UserDto,['password','email']){}

  