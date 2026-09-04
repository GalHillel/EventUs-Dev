import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { ProfilePic } from './profilePic.model';
import { CreateProfilePicDto } from '../dto/profilePic.dto';
//import { string } from '../dto/id.dto';

@Injectable()
export class ProfilePicService {
  constructor(@InjectModel(ProfilePic.name) private readonly profilePicModel: Model<ProfilePic>) {}

  async createProfilePic(createProfilePicDto:CreateProfilePicDto): Promise<ProfilePic> {
    
    const profilePic =  new this.profilePicModel(createProfilePicDto);
    return profilePic.save();
  }

  async findAllProfilePics(): Promise<ProfilePic[]> {
    return this.profilePicModel.find().exec();
  }

  /**
   * Get profile pic data as buffer
   * @param _id _id field of the desired profilePic
   * @returns profile pic icon
   */
  async getIcon(_id:string): Promise<Buffer>{
    
    return (await this.getProfilePic(_id,'icon')).icon; 
  }

  /**
   * Get profilePic by profilePic Id
   * @param _id _id field of the desired profilePic
   * @param field get only selected fields from profilePic
   * @returns Desired profilePic
   */
  async getProfilePic(_id:string, field?:string): Promise<ProfilePic>{
    return this.profilePicModel.findById(_id,field).exec().then((pic) => { 
        if (!pic) throw new NotFoundException('profile pic '+_id+' not Found');
        return pic;
      }
    )
  }

  async getDecodedIcon(_id:string): Promise<string>{
    return (await this.getProfilePic(_id,'icon')).icon.toString('base64');
  }

  // Implement other CRUD operations as needed
}