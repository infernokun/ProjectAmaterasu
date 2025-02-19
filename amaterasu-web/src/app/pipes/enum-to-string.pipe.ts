import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'enumToString'
})
export class EnumToStringPipe implements PipeTransform {
  transform(value: any, enumType: any): string {
    return enumType[value] || value;
  }
}
