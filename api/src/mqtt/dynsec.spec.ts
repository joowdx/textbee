import { rotateDeviceClientCommands, rotationErrors } from './dynsec'

describe('dynsec commands', () => {
  it('replaces the device client and pins its role to its own topics', () => {
    expect(rotateDeviceClientCommands('dev1', 'secret')).toEqual([
      { command: 'deleteClient', username: 'device-dev1' },
      { command: 'deleteRole', rolename: 'device-dev1' },
      {
        command: 'createRole',
        rolename: 'device-dev1',
        acls: [
          { acltype: 'publishClientSend', topic: 'textbee/devices/dev1/#', priority: 0, allow: true },
          { acltype: 'subscribePattern', topic: 'textbee/devices/dev1/#', priority: 0, allow: true },
          { acltype: 'publishClientReceive', topic: 'textbee/devices/dev1/#', priority: 0, allow: true },
        ],
      },
      {
        command: 'createClient',
        username: 'device-dev1',
        password: 'secret',
        roles: [{ rolename: 'device-dev1', priority: 0 }],
      },
    ])
  })

  it('ignores failed deletes and reports failed creates', () => {
    expect(
      rotationErrors([
        { command: 'deleteClient', error: 'Client not found' },
        { command: 'deleteRole', error: 'Role not found' },
        { command: 'createRole' },
        { command: 'createClient', error: 'Client already exists' },
      ]),
    ).toEqual(['createClient: Client already exists'])
  })
})
