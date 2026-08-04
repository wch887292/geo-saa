<template>
  <div class="permission" v-loading="pageLoading">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="用户管理" name="users">
        <div class="tab-toolbar">
          <el-button type="primary" size="small" @click="openUserDialog(false)">新增用户</el-button>
        </div>
        <el-table :data="userList" stripe style="width:100%">
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="nickname" label="昵称" width="140" />
          <el-table-column prop="role" label="角色" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="openUserDialog(true, row)">编辑</el-button>
              <el-button
                size="small"
                :type="row.status === 'active' ? 'warning' : 'success'"
                link
                @click="toggleUserStatus(row)"
              >
                {{ row.status === 'active' ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrap">
          <el-pagination v-model:current-page="userPage" :page-size="10" :total="userTotal" layout="prev, pager, next" small />
        </div>
      </el-tab-pane>

      <el-tab-pane label="角色管理" name="roles">
        <div class="tab-toolbar">
          <el-button type="primary" size="small" @click="showRoleDialog = true">新增角色</el-button>
        </div>
        <el-table :data="roleList" stripe style="width:100%">
          <el-table-column prop="name" label="角色名称" width="160" />
          <el-table-column prop="code" label="角色编码" width="160" />
          <el-table-column prop="description" label="描述" min-width="200" />
          <el-table-column prop="userCount" label="用户数" width="80" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="configRolePermission(row)">权限配置</el-button>
              <el-button size="small" type="danger" link>删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="logs">
        <div class="tab-toolbar">
          <el-input v-model="logSearch" placeholder="搜索操作内容" style="width:240px" clearable @input="handleLogSearch" />
          <el-button size="small" @click="exportLogs">导出日志</el-button>
        </div>
        <el-table :data="filteredLogs" stripe style="width:100%">
          <el-table-column prop="user" label="操作人" width="120" />
          <el-table-column prop="module" label="模块" width="120" />
          <el-table-column prop="action" label="操作内容" min-width="200" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" width="140" />
          <el-table-column prop="time" label="时间" width="170" />
        </el-table>
        <div class="pagination-wrap">
          <el-pagination v-model:current-page="logPage" :page-size="10" :total="filteredLogs.length" layout="prev, pager, next" small />
        </div>
      </el-tab-pane>

      <el-tab-pane label="登录日志" name="login-logs">
        <el-table :data="loginLogs" stripe style="width:100%">
          <el-table-column prop="user" label="用户" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : 'danger'">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="140" />
          <el-table-column prop="device" label="设备" min-width="180" show-overflow-tooltip />
          <el-table-column prop="time" label="时间" width="170" />
        </el-table>
        <div class="pagination-wrap">
          <el-pagination v-model:current-page="loginLogPage" :page-size="10" :total="loginLogs.length" layout="prev, pager, next" small />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showUserDialog" :title="isEditUser ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="userForm" label-width="100px" :rules="userRules" ref="userFormRef">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="userForm.nickname" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEditUser">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" style="width:100%">
            <el-option v-for="r in roleList" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUser">{{ isEditUser ? '更新' : '保存' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRoleDialog" title="新增角色" width="500px">
      <el-form :model="roleForm" label-width="100px">
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.name" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="roleForm.code" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPermissionDialog" title="权限配置" width="500px">
      <el-tree
        :data="permissionTree"
        show-checkbox
        default-expand-all
        node-key="id"
        ref="permissionTreeRef"
        :props="{ label: 'label', children: 'children' }"
      />
      <template #footer>
        <el-button @click="showPermissionDialog = false">取消</el-button>
        <el-button type="primary" @click="savePermission">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const pageLoading = ref(false)
const activeTab = ref('users')
const showUserDialog = ref(false)
const showRoleDialog = ref(false)
const showPermissionDialog = ref(false)
const isEditUser = ref(false)
const editingUser = ref(null)
const userPage = ref(1)
const userTotal = ref(4)
const logPage = ref(1)
const loginLogPage = ref(1)
const logSearch = ref('')

const userFormRef = ref(null)
const permissionTreeRef = ref(null)

const userForm = reactive({
  username: '',
  nickname: '',
  password: '',
  role: ''
})

const userRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const roleForm = reactive({
  name: '',
  code: '',
  description: ''
})

const userList = ref([
  { id: 1, username: 'admin', nickname: '管理员', role: '超级管理员', status: 'active', createdAt: '2024-01-01 00:00' },
  { id: 2, username: 'operator', nickname: '运营', role: '运营管理员', status: 'active', createdAt: '2024-03-15 10:00' },
  { id: 3, username: 'editor', nickname: '编辑', role: '内容编辑', status: 'active', createdAt: '2024-04-20 14:30' },
  { id: 4, username: 'viewer', nickname: '观察者', role: '数据查看者', status: 'disabled', createdAt: '2024-05-10 09:00' }
])

const roleList = ref([
  { id: 1, name: '超级管理员', code: 'super_admin', description: '系统最高权限', userCount: 2 },
  { id: 2, name: '运营管理员', code: 'ops_admin', description: '运营相关权限', userCount: 5 },
  { id: 3, name: '内容编辑', code: 'content_editor', description: '内容创作与编辑权限', userCount: 8 },
  { id: 4, name: '数据查看者', code: 'data_viewer', description: '仅查看数据看板', userCount: 12 }
])

const permissionTree = ref([
  {
    id: 1, label: '系统管理',
    children: [
      { id: 11, label: '用户管理' },
      { id: 12, label: '角色管理' },
      { id: 13, label: '模型配置' }
    ]
  },
  {
    id: 2, label: '诊断中心',
    children: [
      { id: 21, label: '发起诊断' },
      { id: 22, label: '查看报告' }
    ]
  },
  {
    id: 3, label: '内容管理',
    children: [
      { id: 31, label: '知识库管理' },
      { id: 32, label: '内容创作' },
      { id: 33, label: '内容分发' }
    ]
  },
  {
    id: 4, label: '数据看板',
    children: [
      { id: 41, label: '查看数据' },
      { id: 42, label: '导出数据' }
    ]
  }
])

const operationLogs = ref([
  { user: 'admin', module: '系统', action: '用户登录', ip: '192.168.1.1', time: '2024-07-28 09:30:00' },
  { user: 'admin', module: '诊断', action: '创建诊断任务 - 品牌A', ip: '192.168.1.1', time: '2024-07-28 10:15:00' },
  { user: 'operator', module: '内容', action: '批量生成内容 - 行业洞察', ip: '192.168.1.2', time: '2024-07-28 11:00:00' },
  { user: 'editor', module: '知识库', action: '编辑知识条目 - 产品介绍', ip: '192.168.1.3', time: '2024-07-28 14:20:00' },
  { user: 'admin', module: '分发', action: '创建分发任务', ip: '192.168.1.1', time: '2024-07-27 16:00:00' },
  { user: 'operator', module: '系统', action: '修改模型配置', ip: '192.168.1.2', time: '2024-07-27 15:30:00' }
])

const loginLogs = ref([
  { user: 'admin', status: 'success', ip: '192.168.1.1', device: 'Chrome Windows', time: '2024-07-28 09:30:00' },
  { user: 'operator', status: 'success', ip: '192.168.1.2', device: 'Safari macOS', time: '2024-07-28 10:00:00' },
  { user: 'editor', status: 'failed', ip: '192.168.1.3', device: 'Firefox Windows', time: '2024-07-28 10:05:00' },
  { user: 'editor', status: 'success', ip: '192.168.1.3', device: 'Firefox Windows', time: '2024-07-28 10:06:00' },
  { user: 'admin', status: 'success', ip: '192.168.1.1', device: 'Chrome Windows', time: '2024-07-27 09:00:00' }
])

const filteredLogs = computed(() => {
  if (!logSearch.value) return operationLogs.value
  return operationLogs.value.filter(log =>
    log.action.includes(logSearch.value) || log.user.includes(logSearch.value)
  )
})

function openUserDialog(edit, row) {
  isEditUser.value = edit
  if (edit && row) {
    editingUser.value = row
    userForm.username = row.username
    userForm.nickname = row.nickname
    userForm.role = row.role
  } else {
    editingUser.value = null
    userForm.username = ''
    userForm.nickname = ''
    userForm.password = ''
    userForm.role = ''
  }
  showUserDialog.value = true
}

function saveUser() {
  userFormRef.value?.validate((valid) => {
    if (!valid) return
    if (isEditUser.value && editingUser.value) {
      Object.assign(editingUser.value, {
        nickname: userForm.nickname,
        role: userForm.role
      })
      ElMessage.success('用户已更新')
    } else {
      userList.value.push({
        id: Date.now(),
        username: userForm.username,
        nickname: userForm.nickname,
        role: userForm.role,
        status: 'active',
        createdAt: new Date().toLocaleString()
      })
      userTotal.value = userList.value.length
      ElMessage.success('用户已创建')
    }
    showUserDialog.value = false
  })
}

function toggleUserStatus(row) {
  row.status = row.status === 'active' ? 'disabled' : 'active'
  ElMessage.success(`用户已${row.status === 'active' ? '启用' : '禁用'}`)
}

function configRolePermission(row) {
  showPermissionDialog.value = true
}

function savePermission() {
  const checkedKeys = permissionTreeRef.value?.getCheckedKeys() || []
  ElMessage.success(`已保存权限配置，共 ${checkedKeys.length} 项权限`)
  showPermissionDialog.value = false
}

function saveRole() {
  if (!roleForm.name || !roleForm.code) {
    ElMessage.warning('请填写角色名称和编码')
    return
  }
  roleList.value.push({
    id: Date.now(),
    name: roleForm.name,
    code: roleForm.code,
    description: roleForm.description,
    userCount: 0
  })
  showRoleDialog.value = false
  roleForm.name = ''
  roleForm.code = ''
  roleForm.description = ''
  ElMessage.success('角色已创建')
}

function handleLogSearch() {
  logPage.value = 1
}

function exportLogs() {
  ElMessage.success('日志导出中，请稍后下载')
}

onMounted(() => {})
</script>

<style scoped>
.permission { padding: 0; }
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>