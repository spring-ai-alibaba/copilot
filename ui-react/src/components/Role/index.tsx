import React from 'react';
import { Role, RoleProps } from './Role';

interface TeamProps {
  members: RoleProps[];
}

export const Team: React.FC<TeamProps> = ({ members }) => {
  return (
    <div className="flex items-center gap-4 p-4">
      {members.map((member) => (
        <Role key={member.name} {...member} />
      ))}
    </div>
  );
};

// 使用示例
const teamMembers: RoleProps[] = [
  {
    name: 'Mike',
    title: 'Team Leader',
    type: 'leader'
  },
  {
    name: 'Emma',
    title: 'Product Manager',
    type: 'manager'
  },
  {
    name: 'Bob',
    title: 'Architect',
    type: 'architect'
  },
  {
    name: 'Alex',
    title: 'Engineer',
    type: 'engineer'
  },
  {
    name: 'David',
    title: 'Data Analyst',
    type: 'analyst'
  }
];

export const TeamExample: React.FC = () => {
  return <Team members={teamMembers} />;
};