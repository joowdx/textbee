import {
  deviceClientUsername,
  deviceRoleName,
  deviceTopicPrefix,
} from './mqtt-config'

// Commands for Mosquitto's dynamic-security plugin. The broker applies them
// live, so device clients are created and rotated without a restart.
export const DYNSEC_REQUEST_TOPIC = '$CONTROL/dynamic-security/v1'
export const DYNSEC_RESPONSE_TOPIC = '$CONTROL/dynamic-security/v1/response'

export type DynsecCommand = { command: string; [key: string]: unknown }

export type DynsecResponse = {
  command: string
  error?: string
  correlationData?: string
  [key: string]: unknown
}

// A device may publish, subscribe and receive only under its own prefix
export function deviceRoleAcls(deviceId: string) {
  const topic = `${deviceTopicPrefix(deviceId)}#`
  return ['publishClientSend', 'subscribePattern', 'publishClientReceive'].map(
    (acltype) => ({ acltype, topic, priority: 0, allow: true }),
  )
}

// Deleting first drops any old password and disconnects a session that used
// it. The delete commands fail harmlessly when nothing exists yet.
export function rotateDeviceClientCommands(
  deviceId: string,
  password: string,
): DynsecCommand[] {
  const username = deviceClientUsername(deviceId)
  const rolename = deviceRoleName(deviceId)
  return [
    { command: 'deleteClient', username },
    { command: 'deleteRole', rolename },
    { command: 'createRole', rolename, acls: deviceRoleAcls(deviceId) },
    {
      command: 'createClient',
      username,
      password,
      roles: [{ rolename, priority: 0 }],
    },
  ]
}

// Only creates are required to succeed; deletes of a missing entry are fine
export function rotationErrors(responses: DynsecResponse[]): string[] {
  return responses
    .filter((r) => r.error && r.command.startsWith('create'))
    .map((r) => `${r.command}: ${r.error}`)
}
