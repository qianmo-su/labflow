const {createApp,h}=Vue;
const plus=(day,n)=>{const d=new Date(day+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10)};
const app=createApp({
 data:()=>({session:{role:'GUEST',user:'',today:new Date().toISOString().slice(0,10)},rooms:[],roomId:null,week:'',cells:[],calendarReady:false,bookings:[],stats:[],view:'calendar',filter:'ALL',modal:'',current:null,form:{},error:'',busy:false,toast:'',timer:null,epoch:0,
 names:{student1:'王思齐',student2:'李嘉宁',teacher1:'陈老师',teacher2:'徐老师',admin:'周管理员'},states:{ALL:'全部',PENDING:'待审批',APPROVED:'已批准',REJECTED:'已驳回',CANCELLED:'已取消'},times:['08:00','08:55','10:00','10:55','14:00','14:55','16:00','16:55']}),
 computed:{
  tabs(){const t=[{id:'calendar',label:'预约课表',icon:'CalendarDays'}];if(this.session.user)t.push({id:'requests',label:this.session.role==='STUDENT'?'我的预约':'申请记录',icon:'ClipboardList'});if(this.session.role==='ADMIN')t.push({id:'rooms',label:'实验室管理',icon:'Building2'},{id:'stats',label:'使用统计',icon:'ChartColumn'});return t},
  viewLabel(){return {calendar:'实验室预约课表',requests:this.session.role==='TEACHER'?'预约审批':this.session.role==='STUDENT'?'我的预约':'申请记录',rooms:'实验室管理',stats:'实验室使用统计'}[this.view]},
  sectionCode(){return {calendar:'01 / SCHEDULING',requests:'02 / REQUESTS',rooms:'03 / LABORATORIES',stats:'04 / ANALYTICS'}[this.view]},
  subtitle(){return {calendar:'全校实验室 · 开放时段与预约排期',requests:'实验申请、排期状态与审批意见',rooms:'实验空间、座位容量与每日开放节次',stats:'周使用率 = 已预约节次 ÷ 扣除检修后的可排节次'}[this.view]},
  room(){return this.rooms.find(r=>r.id===this.roomId)},days(){return this.week?Array.from({length:7},(_,i)=>plus(this.week,i)):[]},
  defaultWeek(){let d=this.session.today;return plus(d,(8-new Date(d+'T12:00:00Z').getUTCDay())%7)},
  rangeLabel(){return this.week?this.week.slice(5).replace('-',' / ')+'  至  '+plus(this.week,6).slice(5).replace('-',' / ')+'，'+this.week.slice(0,4):''},
  bySlot(){return new Map(this.cells.map(c=>[c.day+'-'+c.period,c]))},
  summary(){let x={free:0,reserved:0,maintenance:0,closed:0};for(const d of this.days)for(let p=1;p<=8;p++)x[this.cell(d,p).type]++;return x},
  filtered(){return this.bookings.filter(b=>this.filter==='ALL'||b.state===this.filter)},
  canReview(){return this.current?.state==='PENDING'&&this.session.role==='TEACHER'},
  canCancel(){return this.session.role==='STUDENT'&&['PENDING','APPROVED'].includes(this.current?.state)&&this.current?.day>=this.session.today},
  dialogTitle(){return {login:this.session.user?'切换身份':'登录平台',apply:'新建实验预约',block:'检修排期',room:this.form.id?'编辑实验室':'新增实验室',review:'申请详情'}[this.modal]},
  actionLabel(){return {login:'登录',apply:'提交预约',block:'保存排期',room:'保存实验室',review:this.canReview?'提交审批':this.canCancel?'取消预约':''}[this.modal]}
 },
 methods:{
  plus,roomName(id){return this.rooms.find(r=>r.id===id)?.name||'未知实验室'},
  async api(path,method='GET',data){let headers={},body;if(method!=='GET'){headers[this.session.header||'X-CSRF-TOKEN']=this.session.csrf;if(data instanceof URLSearchParams)body=data;else if(data){headers['Content-Type']='application/json';body=JSON.stringify(data)}}
   const r=await fetch(path,{method,headers,body,cache:'no-store'});let text=await r.text(),result;try{result=text?JSON.parse(text):null}catch{result=null}
   if(!r.ok)throw new Error(result?.message||({400:'请求格式不正确',401:'登录失效或密码错误',403:'无权限或会话已过期，请刷新页面重新登录'}[r.status])||'请求失败，请重试');return result},
  async loadSession(){this.session=await this.api('/api/session')},
  notify(s){clearTimeout(this.timer);this.toast=s;this.timer=setTimeout(()=>this.toast='',4200)},
  async refresh(){let token=++this.epoch;try{
    if(this.view==='calendar'&&this.roomId){this.calendarReady=false;const a=await this.api(`/api/calendar?room=${this.roomId}&start=${this.week}`);if(token===this.epoch){this.cells=a;this.calendarReady=true}}
    if(this.view==='requests'){const a=await this.api('/api/bookings');if(token===this.epoch)this.bookings=a}
    if(this.view==='stats'){const a=await this.api('/api/admin/stats?start='+this.week);if(token===this.epoch)this.stats=a}
   }catch(e){if(token===this.epoch)this.notify(e.message)}},
  async loadRooms(){this.rooms=await this.api('/api/rooms');if(!this.rooms.some(r=>r.id===this.roomId))this.roomId=this.rooms[0]?.id},
  async go(v){this.view=v;this.filter='ALL';await this.refresh()},async chooseRoom(id){this.roomId=id;await this.refresh()},
  async setWeek(w){this.week=w;await this.refresh()},shift(n){return this.setWeek(plus(this.week,n))},
  cell(day,p){if(!this.calendarReady)return {type:'closed',label:'…'};return this.bySlot.get(day+'-'+p)||(day<this.session.today||day>plus(this.session.today,90)||!this.room||p<this.room.firstPeriod||p>this.room.lastPeriod?{type:'closed',label:'未开放'}:{type:'free',label:'可申请'})},
  show(id,data){this.error='';this.form=data;this.modal=id;this.$nextTick(()=>document.querySelector('.dialog select,.dialog input,.dialog button')?.focus())},dismiss(){if(!this.busy)this.modal=''},
  openLogin(){this.show('login',{account:this.session.user||'student1',password:'LabDemo123'})},
  pick(day,p){if(!this.calendarReady)return;const c=this.cell(day,p);if(this.session.role==='ADMIN'&&['free','maintenance'].includes(c.type)){this.blockDialog(this.room,day,p,c.type==='maintenance'?'release':'block');return}
   if(c.type!=='free'){this.notify('第 '+p+' 节 · '+c.label);return}this.startBooking(day,p)},
  startBooking(d,p){if(!this.session.user){this.openLogin();return}if(this.session.role!=='STUDENT'){this.notify('仅学生可提交预约');return}
    this.show('apply',{labId:this.roomId,day:d||((this.week>=this.session.today&&this.week<=plus(this.session.today,90))?this.week:this.session.today),firstPeriod:p||this.room.firstPeriod,lastPeriod:p||this.room.firstPeriod,purpose:''})},
  roomDialog(r){this.show('room',r?{...r}:{name:'',location:'',seats:32,firstPeriod:1,lastPeriod:8})},
  blockDialog(r,d,p,mode){const lab=r||this.room;this.show('block',{labId:lab.id,day:d||this.defaultWeek,firstPeriod:p||lab.firstPeriod,lastPeriod:p||lab.firstPeriod,mode:mode||'block',note:''})},
  reviewDialog(b){this.current=b;this.show('review',{decision:'yes',opinion:''})},
  async signout(){try{await this.api('/logout','POST');await this.loadSession();this.modal='';await this.go('calendar');this.notify('已退出登录')}catch(e){this.error=e.message}},
  async submit(){if(this.busy)return;this.busy=true;this.error='';const f=this.form;try{
    if(this.modal==='login'){await this.api('/login','POST',new URLSearchParams({username:f.account,password:f.password}));await this.loadSession();await this.go('calendar');this.notify('已登录 · '+this.session.name)}
    else if(this.modal==='apply'){await this.api('/api/student/bookings','POST',f);this.notify('预约已提交，等待教师审批')}
    else if(this.modal==='room'){await this.api('/api/admin/rooms'+(f.id?'/'+f.id:''),f.id?'PUT':'POST',f);await this.loadRooms();this.notify('实验室资料已保存')}
    else if(this.modal==='block'){await this.api('/api/admin/maintenance'+(f.mode==='release'?'/release':''),'POST',f);this.notify('检修排期已更新')}
    else if(this.modal==='review'&&this.canReview){await this.api(`/api/teacher/bookings/${this.current.id}/review`,'POST',{approve:f.decision==='yes',opinion:f.opinion});this.notify(f.decision==='yes'?'审批成功，节次已占用':'已驳回申请')}
    else if(this.modal==='review'&&this.canCancel){await this.api(`/api/student/bookings/${this.current.id}/cancel`,'POST');this.notify('预约已取消')}
    this.modal='';await this.refresh()
   }catch(e){this.error=e.message}finally{this.busy=false}}
 },
 async mounted(){try{await this.loadSession();this.week=this.defaultWeek;await this.loadRooms();await this.refresh()}catch(e){this.notify(e.message)}}
});
app.component('ico',{props:['n'],render(){const data=lucide.icons[this.n]||[];return h('svg',{class:'ico',viewBox:'0 0 24 24',fill:'none',stroke:'currentColor','stroke-width':1.7,'stroke-linecap':'round','stroke-linejoin':'round','aria-hidden':'true'},data.map(([tag,attrs])=>h(tag,attrs)))}});
app.mount('#app');
